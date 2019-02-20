// -*- mode: groovy -*-

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// This file contains the steps that will be used in the 
// Jenkins pipelines

utils = load('ci/Jenkinsfile_utils.groovy')

// Python wheels
mx_pip = 'wheel_build/dist/*.whl'

mx_lib = 'lib/libmxnet.so, lib/libgfortran.so.3, lib/libquadmath.so.0'
mx_mkldnn_lib = 'lib/libmxnet.so, lib/libgfortran.so.3, lib/libquadmath.so.0, lib/libiomp5.so, lib/libmkldnn.so.0, lib/libmklml_intel.so, MKLML_LICENSE'


def docker_run(platform, function_name, use_nvidia, shared_mem = '500m', env_vars = "") {
  def command = "ci/build.py %ENV_VARS% --docker-registry ${env.DOCKER_CACHE_REGISTRY} %USE_NVIDIA% --platform %PLATFORM% --docker-build-retries 3 --shm-size %SHARED_MEM% /work/mxnet/cd/runtime_functions.sh %FUNCTION_NAME%"
  command = command.replaceAll('%ENV_VARS%', env_vars.length() > 0 ? "-e ${env_vars}" : '')
  command = command.replaceAll('%USE_NVIDIA%', use_nvidia ? '--nvidiadocker' : '')
  command = command.replaceAll('%PLATFORM%', platform)
  command = command.replaceAll('%FUNCTION_NAME%', function_name)
  command = command.replaceAll('%SHARED_MEM%', shared_mem)

  sh command
}

// Python unittest for CPU
// Python 2
def python2_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python2_cpu', false)
  }
}

// Python 3
def python3_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_cpu', false)
  }
}

// Python 3
def python3_ut_asan(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_cpu_asan', false)
  }
}

def python3_ut_mkldnn(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_cpu_mkldnn', false)
  }
}

// GPU test has two parts. 1) run unittest on GPU, 2) compare the results on
// both CPU and GPU
// Python 2
def python2_gpu_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python2_gpu', true)
  }
}

// Python 3
def python3_gpu_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_gpu', true)
  }
}

// Python 3 NOCUDNN
def python3_gpu_ut_nocudnn(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_gpu_nocudnn', true)
  }
}

//------------------------------------------------------------------------------------------

def docker_build_cpu(variant, mxnet_version, is_python3) {

  def python_version = is_python3 ? 'py3' : 'py2'
  def python_command = is_python3 ? 'python3' : 'python'

  def dockerfile = "cd/Dockerfile.${python_version}"

  def tag = "perdasilva/python:${mxnet_version}_cpu"
  if (variant == "mkl") {
    tag += "_mkl"
  }
  if (is_python3) {
    tag += "_py3"
  }

  def context = 'wheel_build/dist'
  def stash_name = variant.endsWith('mkl') ? 'pip_mkldnn_cpu' : 'pip_cpu'
  def platform = 'ubuntu:16.04'

  return ["Docker Build: ${tag}": {
      node(NODE_LINUX_CPU) {
        ws("workspace/docker-build-release-cpu-${python_version}") {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("${stash_name}", mx_pip, false)
            sh "docker build -t ${tag} -f ${dockerfile} --build-arg PLATFORM=${platform} ${context}"
            sh """docker run -v `pwd`:/mxnet ${tag} bash -c "${python_command} /mxnet/tests/python/train/test_conv.py" """
            sh """docker run -v `pwd`:/mxnet ${tag} bash -c "${python_command} /mxnet/example/image-classification/train_mnist.py" """
          }
        }
      }
    }]
}

def pip_package_unix() {
  return ['Package: CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/package-static-cpu') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("cpu", mx_lib, false)
            docker_run('publish.ubuntu1404_cpu', "package_static_python cpu", false)
            utils.pack_lib("pip_cpu", mx_pip, false)
          }
        }
      }
    }]
}

def pip_package_unix_mkl() {
  return ['Package: CPU-MKL': {
      node(NODE_LINUX_CPU) {
        ws("workspace/package-static-cpu-mkl") {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("mkldnn_cpu", mx_mkldnn_lib, false)
            docker_run('publish.ubuntu1404_cpu', "package_static_python mkl", false)
            utils.pack_lib("pip_mkldnn_cpu", mx_pip, false)
          }
        }
      }
    }]
}

def pip_package_unix_gpu(variant) {
  return ["Package: GPU-${variant}": {
      node(NODE_LINUX_CPU) {
        ws("workspace/package-static-gpu-${variant}") {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("gpu_${variant}", mx_lib, false)
            docker_run("ubuntu_gpu_${variant}", "package_static_python ${variant}", false)
            utils.pack_lib("pip_gpu_${variant}", mx_pip, false)
          }
        }
      }
    }]
}

def pip_package_unix_gpu_mkl(variant) {
  return ["Package: GPU-MKL-${variant}": {
      node(NODE_LINUX_CPU) {
        ws("workspace/package-static-gpu-mkl-${variant}") {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("mkldnn_gpu_${variant}", mx_mkldnn_lib, false)
            docker_run("ubuntu_gpu_${variant}", "package_static_python ${variant}mkl", false)
            utils.pack_lib("pip_mkldnn_gpu_${variant}", mx_pip, false)
          }
        }
      }
    }]
}

def compile_unix_static_cpu() {
  return ['CPU: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cpu') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_cpu', 'build_static_python cpu', false)
            utils.pack_lib('cpu', mx_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cpu_mkl() {
  return ['CPU-MKL: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cpu-mkl') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_cpu', 'build_static_python mkl', false)
            utils.pack_lib('mkldnn_cpu', mx_mkldnn_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu80() {
  return ['CU80: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu80') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_gpu', 'build_static_python cu80', false)
            utils.pack_lib('gpu_cu80', mx_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu80_mkl() {
  return ['CU80MKL: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu80-mkl') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_gpu', 'build_static_python cu80mkl', false)
            utils.pack_lib('mkldnn_gpu_cu80', mx_mkldnn_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu90() {
  return ['CU90: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu90') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_gpu', 'build_static_python cu90', false)
            utils.pack_lib('gpu_cu90', mx_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu90_mkl() {
  return ['CU90MKL: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu90-mkl') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_gpu', 'build_static_python cu90mkl', false)
            utils.pack_lib('mkldnn_gpu_cu90', mx_mkldnn_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu92() {
  return ['CU92: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu92') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_gpu', 'build_static_python cu92', false)
            utils.pack_lib('gpu_cu92', mx_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu92_mkl() {
  return ['CU92MKL: Static': {
      node(NODE_LINUX_CsPU) {
        ws('workspace/build-static-cu92-mkl') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_gpu', 'build_static_python cu92mkl', false)
            utils.pack_lib('mkldnn_gpu_cu92', mx_mkldnn_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu100() {
  return ['CU100: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu10') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_cpu', 'build_static_python cu100', false)
            utils.pack_lib('gpu_cu100', mx_lib, false)
          }
        }
      }
    }]
}

def compile_unix_static_cu100_mkl() {
  return ['CU100MKL: Static': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cu10-mkl') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.init_git()
            docker_run('publish.ubuntu1404_cpu', 'build_static_python cu100mkl', false)
            utils.pack_lib('mkldnn_gpu_cu100', mx_mkldnn_lib, false)
          }
        }
      }
    }]
}


def test_unix_python2_cpu() {
    return ['Python2: CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python2-cpu') {
          try {
            utils.unpack_and_init('cpu', mx_lib, false)
            python2_ut('ubuntu_cpu')
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python2_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_train.xml', 'nosetests_python2_cpu_train.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python2_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python2_gpu(variant) {
    return ["Python2: GPU-${variant}": {
      node(NODE_LINUX_GPU) {
        ws('workspace/ut-python2-gpu') {
          try {
            utils.unpack_and_init("gpu_${variant}", mx_lib, false)
            python2_gpu_ut("ubuntu_gpu_${variant}")
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python2_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python2_quantize_gpu(variant) {
    return ["Python2: Quantize GPU-${variant}": {
      node(NODE_LINUX_GPU_P3) {
        ws('workspace/ut-python2-quantize-gpu') {
          timeout(time: max_time, unit: 'MINUTES') {
            try {
              utils.unpack_and_init("gpu_${variant}", mx_lib, false)
              docker_run("ubuntu_gpu_${variant}", 'unittest_ubuntu_python2_quantization_gpu', true)
              utils.publish_test_coverage()
            } finally {
              utils.collect_test_results_unix('nosetests_quantization_gpu.xml', 'nosetests_python2_quantize_gpu.xml')
            }
          }
        }
      }
    }]
}

def test_unix_python2_mkldnn_gpu(variant) {
    return ["Python2: MKLDNN-GPU-${variant}": {
      node(NODE_LINUX_GPU) {
        ws('workspace/ut-python2-mkldnn-gpu') {
          try {
            utils.unpack_and_init("mkldnn_gpu_${variant}", mx_mkldnn_lib, false)
            python2_gpu_ut("ubuntu_gpu_${variant}")
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python2_mkldnn_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python3_cpu() {
    return ['Python3: CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python3-cpu') {
          try {
            utils.unpack_and_init('cpu', mx_lib, false)
            python3_ut('ubuntu_cpu')
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python3_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python3_mkl_cpu() {
    return ['Python3: MKL-CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python3-cpu') {
          try {
            utils.unpack_and_init('cpu_mkl', mx_lib, false)
            python3_ut('ubuntu_cpu')
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python3_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python3_gpu(variant) {
    return ["Python3: GPU-${variant}": {
      node(NODE_LINUX_GPU) {
        ws('workspace/ut-python3-gpu') {
          try {
            utils.unpack_and_init("gpu_${variant}", mx_lib, false)
            python3_gpu_ut("ubuntu_gpu_${variant}")
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python3_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python3_quantize_gpu(variant) {
    return ["Python3: Quantize GPU-${variant}": {
      node(NODE_LINUX_GPU_P3) {
        ws('workspace/ut-python3-quantize-gpu') {
          timeout(time: max_time, unit: 'MINUTES') {
            try {
              utils.unpack_and_init("gpu_${variant}", mx_lib, false)
              docker_run("ubuntu_gpu_${variant}", 'unittest_ubuntu_python3_quantization_gpu', true)
              utils.publish_test_coverage()
            } finally {
              utils.collect_test_results_unix('nosetests_quantization_gpu.xml', 'nosetests_python3_quantize_gpu.xml')
            }
          }
        }
      }
    }]
}

def test_unix_python2_mkldnn_cpu() {
    return ['Python2: MKLDNN-CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python2-mkldnn-cpu') {
          try {
            utils.unpack_and_init('mkldnn_cpu', mx_mkldnn_lib, false)
            python2_ut('ubuntu_cpu')
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python2_mkldnn_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_train.xml', 'nosetests_python2_mkldnn_cpu_train.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python2_mkldnn_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python3_mkldnn_cpu() {
    return ['Python3: MKLDNN-CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python3-mkldnn-cpu') {
          try {
            utils.unpack_and_init('mkldnn_cpu', mx_mkldnn_lib, false)
            python3_ut_mkldnn('ubuntu_cpu')
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_mkldnn_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_mkl.xml', 'nosetests_python3_mkldnn_cpu_mkl.xml')
          }
        }
      }
    }]
}

def test_unix_python3_mkldnn_mkl_cpu() {
    return ['Python3: MKLDNN-MKL-CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python3-mkldnn-mkl-cpu') {
          try {
            utils.unpack_and_init('mkldnn_cpu', mx_mkldnn_lib, false)
            python3_ut_mkldnn('ubuntu_cpu')
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_mkldnn_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_mkl.xml', 'nosetests_python3_mkldnn_cpu_mkl.xml')
          }
        }
      }
    }]
}

def test_unix_python3_mkldnn_gpu(variant) {
    return ["Python3: MKLDNN-GPU-${variant}": {
      node(NODE_LINUX_GPU) {
        ws('workspace/ut-python3-mkldnn-gpu') {
          try {
            utils.unpack_and_init("mkldnn_gpu_${variant}", mx_mkldnn_lib, false)
            python3_gpu_ut("ubuntu_gpu_${variant}")
            utils.publish_test_coverage()
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python3_mkldnn_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python3_integration_gpu(variant) {
    return ["Python Integration GPU-${variant}": {
      node(NODE_LINUX_GPU) {
        ws('workspace/it-python-gpu') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("gpu_${variant}", mx_lib, false)
            docker_run("ubuntu_gpu_${variant}", 'integrationtest_ubuntu_gpu_python', true)
            utils.publish_test_coverage()
          }
        }
      }
    }]
}

def test_unix_distributed_kvstore_cpu() {
    return ['dist-kvstore tests CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/it-dist-kvstore') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init('cpu', mx_lib, false)
            docker_run('ubuntu_cpu', 'integrationtest_ubuntu_cpu_dist_kvstore', false)
            utils.publish_test_coverage()
          }
        }
      }
    }]
}

def test_unix_distributed_kvstore_gpu(variant) {
    return ["dist-kvstore tests GPU-${variant}": {
      node(NODE_LINUX_GPU) {
        ws('workspace/it-dist-kvstore') {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("gpu_${variant}", mx_lib, false)
            docker_run("ubuntu_gpu_${variant}", 'integrationtest_ubuntu_gpu_dist_kvstore', true)
            utils.publish_test_coverage()
          }
        }
      }
    }]
}

return this
