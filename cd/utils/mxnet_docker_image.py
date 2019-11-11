#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import argparse
import logging
import os
import re
import subprocess
import sys

from os.path import join, dirname, abspath
from subprocess import Popen
from typing import Dict, List, Optional, Set

SCRIPT_PATH = dirname(abspath(__file__))
CI_DIRECTORY_PATH = join(SCRIPT_PATH, "..", "..", "ci")

LATEST_GPU_VARIANT = "cu90"
LATEST_GPU_MKL_VARIANT = "{}mkl".format(LATEST_GPU_VARIANT)

MXNET_VARIANTS = ["cpu", "cpu_mkl", "cu90", "cu90mkl", "cu92", "cu92mkl", "cu100", "cu100mkl", "cu101", "cu101mkl"]

MXNET_IMAGE_TYPE_ROOT_DIRS = {
    "python": "python/docker",
    "runtime": "runtime_images",
}

UBUNTU_IMAGE = "ubuntu:16.04"
CUDA_IMAGE = "nvidia/cuda"
CUDNN_VERSION = 7


class UnrecognizedMXNetVariantError(ValueError):
    def __init__(self, mxnet_variant):
        super().__init__("Unrecognized mxnet variant '{}'".format(mxnet_variant))


def _execute_command_and_stream_output(cmd: List[str]):
    process = Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in process.stdout:
        logging.info(str(line, encoding="utf-8").strip())
    return process.wait()


def _check_execution(cmd: List[str], exception_message: str):
    if _execute_command_and_stream_output(cmd) != 0:
        raise RuntimeError(exception_message)


def get_cuda_version_from_mxnet_variant(mxnet_cuda_variant: str) -> str:
    """
    Returns the cuda version from a cuda mxnet variant. Examples:
        cu90 => 9.0,
        cu101mkl => 10.1
    :param mxnet_cuda_variant: any mxnet variant starting format "cu(\d+)(mkl)?"
    :return: The cuda version
    """
    assert mxnet_cuda_variant is not None
    m = re.match(r"cu(?P<cuda_version>\d+)(mkl)?", mxnet_cuda_variant)
    if not m:
        raise ValueError("Cannot parse mxnet variant '{}'.".format(mxnet_cuda_variant))
    cuda_version = m.groupdict()["cuda_version"]
    return "{}.{}".format(cuda_version[:-1], cuda_version[len(cuda_version) - 1:])


def get_base_ubuntu_image(mxnet_variant: str) -> str:
    """
    Returns the ubuntu-based base image for the mxnet given mxnet_variant. E.g.
        cpu => ubuntu:16.04
        cu101 => nvidia/cuda:10.1-cudnn7-runtime-ubuntu16.04
        cu92mkl => nvidia/cuda:9.2-cudnn7-runtime-ubuntu16.04
    :param mxnet_variant: The mxnet variant
    :return: The ubuntu-based base image for a given mxnet variant
    """
    assert mxnet_variant is not None
    if mxnet_variant.startswith("cu"):
        cuda_version = get_cuda_version_from_mxnet_variant(mxnet_variant)
        return "{}:{}-cudnn{}-runtime-{}".format(CUDA_IMAGE, cuda_version, CUDNN_VERSION, UBUNTU_IMAGE.replace(":", ""))
    elif mxnet_variant in ["cpu", "cpu_mkl"]:
        return UBUNTU_IMAGE
    else:
        raise UnrecognizedMXNetVariantError(mxnet_variant)


def get_image_tags(
    mxnet_variant: str,
    is_release: bool,
    version: str,
    tag_postfix: Optional[str] = None,
    include_latest: bool = False
) -> List[str]:
    """
    Returns a list mxnet docker image tag - the first tag is the "main" tag. I.e. not latest, latest_cpu, etc.
    But, 1.5.0_cpu, nightly_cu90, etc.
    In the case where tag_postfix is set, the latest tag will only be included in the list if include_latest is True.
    """
    assert mxnet_variant is not None
    assert is_release is not None
    assert version is not None

    image_tags = []
    if mxnet_variant == "cpu":
        tag_suffix = "cpu"
    elif mxnet_variant == "cpu_mkl":
        tag_suffix = "cpu_mkl"
    elif mxnet_variant.startswith("cu"):
        tag_suffix = "gpu_{}".format(mxnet_variant)
        if tag_suffix.endswith("mkl"):
            tag_suffix = tag_suffix[:-3] + "_mkl"
    else:
        raise UnrecognizedMXNetVariantError(mxnet_variant)

    image_tags.append("{}_{}".format(version, tag_suffix))

    if is_release:
        if mxnet_variant == "cpu":
            image_tags.append("latest_cpu")
        elif mxnet_variant == "mkl":
            image_tags.append("latest_cpu_mkl")
        elif mxnet_variant == LATEST_GPU_VARIANT:
            image_tags.append("latest_gpu")
        elif mxnet_variant == LATEST_GPU_MKL_VARIANT:
            image_tags.append("latest_gpu_mkl")

    if tag_postfix:
        image_tags = ["{}_{}".format(tag, tag_postfix) for tag in image_tags]
        if include_latest:
            image_tags.append("latest")

    return image_tags


def get_image_tag(mxnet_variant: str, is_release: bool, version: str, tag_postfix: Optional[str] = None) -> str:
    """
    Returns the "main" image tag for a given mxnet image.
    E.g. the cpu variant might have several tags, e.g. 1.5.0_cpu, latest, latest_cpu, etc.
    The first tag [cpu] is specific for this image, where as the others [latest, latest_cpu] might change with
    each release
    """
    return next(iter(get_image_tags(mxnet_variant, is_release, version, tag_postfix)))


def get_full_image_name(image_name: str, image_tag: str, docker_registry: Optional[str] = None) -> str:
    """
    The the full image name (docker_registry/)image_name:image_tag
    """
    name = "{}:{}".format(image_name,image_tag)
    if docker_registry:
        return "{}/{}".format(docker_registry, name)
    return name


def _docker_build(
    image_name: str,
    resource_directory: str,
    docker_build_context: str,
    build_args: Set[str] = None,
    dockerfile: Optional[str] = None
):
    """
    Builds a docker image
    """
    if not build_args:
        build_args = {}
    if not dockerfile:
        dockerfile = join(resource_directory, "Dockerfile")
    cmd = ["docker", "build", "-t", image_name]
    for build_arg in build_args:
        cmd.extend(["--build-arg", build_arg])
    cmd.extend(["-f", dockerfile, docker_build_context])
    logging.info("Building image with command: '{}'".format(" ".join(cmd)))
    _check_execution(cmd, "Failed to build image '{}'. See logs for details...".format(image_name))


def build(args: argparse.Namespace):
    """
    Build the MXNet docker image specified by the command line arguments
    """
    def sanitize_docker_build_args(docker_build_args: List[str]):
        clean_build_args = []
        for build_arg in docker_build_args:
            if build_arg.startswith("BASE_IMAGE"):
                logging.warning("Ignoring supplied build-arg '{}'".format(build_arg))
            else:
                clean_build_args.append(build_arg)
        return clean_build_args

    build_args = sanitize_docker_build_args(args.build_args)
    image_tag = get_image_tag(args.mxnet_variant, args.is_release, args.version, args.tag_postfix)
    image_name = get_full_image_name(args.image_name, image_tag)
    base_image = get_base_ubuntu_image(args.mxnet_variant)
    _docker_build(
        image_name=image_name,
        resource_directory=args.image_root_directory,
        docker_build_context=args.docker_build_context_directory,
        build_args={*build_args, "BASE_IMAGE={}".format(base_image)}
    )


def test(args: argparse.Namespace):
    """
    Tests an MXNet docker image. This is done by first building a test image based on the mxnet image to be tested.
    Then, the "image_test_command" is executed on the image. The image is said to have passed the tests if the
    command does not fail. The mxnet root directory is mounted to /work/mxnet on the container. The command is
    executed with the host's user to avoid root ownership of any files create during the test process on the host
    filesystem.
    """
    image_tag = get_image_tag(args.mxnet_variant, args.is_release, args.version, args.tag_postfix)
    mxnet_image = get_full_image_name(args.image_name, image_tag)
    test_image_name = "{}_test".format(mxnet_image)
    user = os.getuid()
    group = os.getgid()

    build_args = {
        # The base image for the test image, is the image being tested
        "BASE_IMAGE={}".format(mxnet_image),

        # The user id and group id need to be baked into the test image
        # So that the test image can be executed as the same user that owns the directory on the file system.
        # This way any files created on the host filesystem from within the docker container will be owned by the user.
        # This avoid Jenkins workspace clean up issues - where certain files are owned by root and cannot be deleted.
        "USER_ID={}".format(user),
        "GROUP_ID={}".format(group)
    }

    # Build the test image from the Dockerfile.test dockerfile located with the script
    # Use the mxnet ci directory as the build context, so we can re-use the docker user create and python requirements
    logging.info("Building test image...")
    _docker_build(
        image_name=test_image_name,
        resource_directory=SCRIPT_PATH,
        docker_build_context=CI_DIRECTORY_PATH,
        build_args=build_args,
        dockerfile=join(SCRIPT_PATH, "resources", "Dockerfile.test")
    )

    logging.info("Running image tests...")

    # import tools from ci directory
    sys.path.append(CI_DIRECTORY_PATH)
    from safe_docker_run import SafeDockerClient
    from util import get_mxnet_root

    docker_client = SafeDockerClient()
    return_code = docker_client.run(
        test_image_name,
        cap_add=["SYS_PTRACE"],
        user="{}:{}".format(user, group),
        volumes={
            get_mxnet_root(): {
                "bind": "/work/mxnet",
                "mode": "rw"
            }
        },
        command=args.image_test_command,
        runtime="nvidia" if args.mxnet_variant.startswith("cu") else None
    )
    if return_code != 0:
        raise RuntimeError("Failed to run test image '{}'. See logs for details...".format(test_image_name))


def push(args: argparse.Namespace):
    """
    Push the mxnet image to dockerhub with all its tags
    """
    # import tools from ci directory
    sys.path.append(CI_DIRECTORY_PATH)
    from docker_login import login_dockerhub, logout_dockerhub

    if args.secret_name:
        endpoint_url = os.environ.get("DOCKERHUB_SECRET_ENDPOINT_URL")
        endpoint_region = os.environ.get("DOCKERHUB_SECRET_ENDPOINT_REGION")
        login_dockerhub(args.secret_name, endpoint_url, endpoint_region)

    image_tags = get_image_tags(
        args.mxnet_variant,
        args.is_release,
        args.version,
        args.tag_postfix,
        args.include_latest_tag
    )
    local_image_name_with_tag = get_full_image_name(args.image_name, image_tags[0])

    for tag in image_tags:
        remote_image_name_with_tag = get_full_image_name(args.image_name, tag, args.docker_registry)
        logging.info("Tagging {} -> {}".format(local_image_name_with_tag, remote_image_name_with_tag))
        _check_execution(
            cmd=["docker", "tag", local_image_name_with_tag, remote_image_name_with_tag],
            exception_message="Error tagging image. Please see logs for details..."
        )
        logging.info("Pushing {} to remote registry".format(remote_image_name_with_tag))
        _check_execution(
            cmd=["docker", "push", remote_image_name_with_tag],
            exception_message="Error pushing image. Please see logs for details..."
        )

    if args.secret_name:
        logout_dockerhub()


def main():
    logging.basicConfig(format="%(asctime)sZ %(levelname)s %(message)s", level=logging.INFO)

    logging.info(dirname(abspath(__file__)))
    parser = argparse.ArgumentParser(
        description="Utility for building/testing/pushing MXNet docker images",
        epilog=""
    )
    parser.add_argument("operation",
                        help="Image operation",
                        choices=["build", "test", "push"])
    parser.add_argument("--image-name",
                        help="Image name. E.g. python, runtime, julia, etc.",
                        choices=MXNET_IMAGE_TYPE_ROOT_DIRS.keys(),
                        required=True)
    parser.add_argument("--mxnet-variant",
                        help="MXNet variant",
                        choices=MXNET_VARIANTS,
                        required=True)
    parser.add_argument("--version",
                        help="MXNet version (default: nightly)",
                        default=os.environ.get("VERSION", "nightly"))
    parser.add_argument("--is-release",
                        help="Whether or not it is for a release build or not",
                        action="store_true",
                        default=os.environ.get("RELEASE_BUILD", "false").lower() == "true")

    # TODO Once python 2 is no longer supported we can probably remove all of this
    #  this is only here to support the python docker image use case, which includes python 2 and python 3.
    #  Examples:
    #     mxnet_variant=cpu, --is-release=False, --tag-postfix=py3, --include-latest-tag=True
    #     tag list = [nightly_cpu_py3] - because --is-release is False only nightly ta1gs get produced
    #     mxnet_variant=cpu, --is-release=True, --tag-postfix=py3, --include-latest-tag=True, --version=1.5.0
    #     tag_list = [1.5.0_cpu_py3, latest_py3, latest]
    #     mxnet_variant=cpu, --is-release=True, --tag-postfix=py2, --include-latest-tag=False, --version=1.5.0
    #     tag_list = [1.5.0_cpu_py2, latest_py2]
    parser.add_argument("--tag-postfix",
                        help="A postfix to add to the image tag. E.g. py2, py3",
                        default=None,
                        required=False)
    parser.add_argument("--include-latest-tag",
                        default=False,
                        action="store_true",
                        required=False,
                        help=("Only matters with --tag-postfix if --is-release is set.",
                              "If set, tag list for will include 'latest' for the appropriate mxnet variants.",
                              "Otherwise, only 'latest_<tag_postfix>' will be included."))

    args, _ = parser.parse_known_args()

    if args.operation == "build":
        parser.add_argument("--image-root-directory",
                            help="Directory containing the Dockerfile",
                            required=True)
        parser.add_argument("--build-args",
                            help="Additional build arguments in the format key=value. E.g. COMMIT_ID=abcd1234",
                            default=[],
                            action="append",
                            required=False)
        parser.add_argument("--docker-build-context-directory",
                            help="Docker build context directory",
                            default=".",
                            required=False)
        build(parser.parse_args())

    if args.operation == "test":
        parser.add_argument("--image-test-command",
                            nargs="+",
                            help="Command or script to execute within the test image",
                            required=True)
        test(parser.parse_args())

    if args.operation == "push":
        parser.add_argument("--docker-registry",
                            help="DockerHub registry name. E.g. mxnet",
                            default=os.environ.get("RELEASE_DOCKERHUB_REPOSITORY", None),
                            required=False)
        parser.add_argument("--secret-name",
                            help="Secrets manager secret name for Dockerhub account",
                            default=os.environ.get("RELEASE_DOCKERHUB_SECRET_NAME", None),
                            required=False)
        push(parser.parse_args())


if __name__ == "__main__":
    main()
