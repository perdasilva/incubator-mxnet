#!/bin/bash

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
#
# build and install are separated so changes to build don't invalidate
# the whole docker cache for the image

set -ex

NOSE_COVERAGE_ARGUMENTS="--with-coverage --cover-inclusive --cover-xml --cover-branches --cover-package=mxnet"
NOSE_TIMER_ARGUMENTS="--with-timer --timer-ok 1 --timer-warning 15 --timer-filter warning,error"
CI_CUDA_COMPUTE_CAPABILITIES="-gencode=arch=compute_52,code=sm_52 -gencode=arch=compute_70,code=sm_70"
CI_CMAKE_CUDA_ARCH_BIN="52,70"

clean_repo() {
    set -ex
    git clean -xfd
    git submodule foreach --recursive git clean -xfd
    git reset --hard
    git submodule foreach --recursive git reset --hard
    git submodule update --init --recursive
}

scala_prepare() {
    # Clean up maven logs
    export MAVEN_OPTS="-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
}

build_ccache_wrappers() {
    set -ex

    if [ -z ${CC+x} ]; then
        echo "No \$CC set, defaulting to gcc";
        export CC=gcc
    fi
     if [ -z ${CXX+x} ]; then
       echo "No \$CXX set, defaulting to g++";
       export CXX=g++
    fi

    # Recommended by CCache: https://ccache.samba.org/manual.html#_run_modes
    # Add to the beginning of path to ensure this redirection is picked up instead
    # of the original ones. Especially CUDA/NVCC appends itself to the beginning of the
    # path and thus this redirect is ignored. This change fixes this problem
    # This hacky approach with symbolic links is required because underlying build
    # systems of our submodules ignore our CMake settings. If they use Makefile,
    # we can't influence them at all in general and NVCC also prefers to hardcode their
    # compiler instead of respecting the settings. Thus, we take this brutal approach
    # and just redirect everything of this installer has been called.
    # In future, we could do these links during image build time of the container.
    # But in the beginning, we'll make this opt-in. In future, loads of processes like
    # the scala make step or numpy compilation and other pip package generations
    # could be heavily sped up by using ccache as well.
    mkdir /tmp/ccache-redirects
    export PATH=/tmp/ccache-redirects:$PATH
    ln -s ccache /tmp/ccache-redirects/gcc
    ln -s ccache /tmp/ccache-redirects/gcc-8
    ln -s ccache /tmp/ccache-redirects/g++
    ln -s ccache /tmp/ccache-redirects/g++-8
    ln -s ccache /tmp/ccache-redirects/nvcc
    ln -s ccache /tmp/ccache-redirects/clang++-3.9
    ln -s ccache /tmp/ccache-redirects/clang-3.9
    ln -s ccache /tmp/ccache-redirects/clang++-5.0
    ln -s ccache /tmp/ccache-redirects/clang-5.0
    ln -s ccache /tmp/ccache-redirects/clang++-6.0
    ln -s ccache /tmp/ccache-redirects/clang-6.0
    ln -s ccache /usr/local/bin/gcc
    ln -s ccache /usr/local/bin/gcc-8
    ln -s ccache /usr/local/bin/g++
    ln -s ccache /usr/local/bin/g++-8
    ln -s ccache /usr/local/bin/nvcc
    ln -s ccache /usr/local/bin/clang++-3.9
    ln -s ccache /usr/local/bin/clang-3.9
    ln -s ccache /usr/local/bin/clang++-5.0
    ln -s ccache /usr/local/bin/clang-5.0
    ln -s ccache /usr/local/bin/clang++-6.0
    ln -s ccache /usr/local/bin/clang-6.0

    export NVCC=ccache

    # Uncomment if you would like to debug CCache hit rates.
    # You can monitor using tail -f ccache-log
    # export CCACHE_LOGFILE=/work/mxnet/ccache-log
    # export CCACHE_DEBUG=1
}

unittest_ubuntu_python2_cpu() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-2.7 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_unittest.xml --verbose tests/python/unittest
    
    # Disabling these tests as test_autograd against the static lib. seems to stall on our infrastructure
    # nosetests-2.7 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_train.xml --verbose tests/python/train
    nosetests-2.7 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_quantization.xml --verbose tests/python/quantization
}

unittest_ubuntu_python3_cpu() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1  # Ignored if not present
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_unittest.xml --verbose tests/python/unittest
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_quantization.xml --verbose tests/python/quantization
}

unittest_ubuntu_python3_cpu_mkldnn() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1  # Ignored if not present
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_unittest.xml --verbose tests/python/unittest
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_mkl.xml --verbose tests/python/mkl
}

unittest_ubuntu_python2_gpu() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1  # Ignored if not present
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-2.7 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_gpu.xml --verbose tests/python/gpu
}

unittest_ubuntu_python3_gpu() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1 # Ignored if not present
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_gpu.xml --verbose tests/python/gpu
}

unittest_ubuntu_python3_gpu_nocudnn() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    export CUDNN_OFF_TEST_ONLY=true
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_gpu.xml --verbose tests/python/gpu
}

# quantization gpu currently only runs on P3 instances
# need to separte it from unittest_ubuntu_python2_gpu()
unittest_ubuntu_python2_quantization_gpu() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1  # Ignored if not present
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-2.7 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_quantization_gpu.xml --verbose tests/python/quantization_gpu
}

# quantization gpu currently only runs on P3 instances
# need to separte it from unittest_ubuntu_python3_gpu()
unittest_ubuntu_python3_quantization_gpu() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_MKLDNN_DEBUG=1 # Ignored if not present
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    nosetests-3.4 $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --with-xunit --xunit-file nosetests_quantization_gpu.xml --verbose tests/python/quantization_gpu
}

unittest_ubuntu_cpu_scala() {
    set -ex
    scala_prepare
    cd scala-package
    mvn -B integration-test
}

integrationtest_ubuntu_gpu_python() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    python example/image-classification/test_score.py
}

integrationtest_ubuntu_cpu_dist_kvstore() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    export MXNET_USE_OPERATOR_TUNING=0
    cd tests/nightly/
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=gluon_step_cpu
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=gluon_sparse_step_cpu
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=invalid_cpu
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=gluon_type_cpu
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --no-multiprecision
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=compressed_cpu
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=compressed_cpu --no-multiprecision
    ../../tools/launch.py -n 3 --launcher local python test_server_profiling.py
}

integrationtest_ubuntu_gpu_scala() {
    set -ex
    scala_prepare
    cd scala-package
    export SCALA_TEST_ON_GPU=1
    mvn -B integration-test -DskipTests=false
}

integrationtest_ubuntu_gpu_dist_kvstore() {
    set -ex
    export PYTHONPATH=./python/
    export MXNET_STORAGE_FALLBACK_LOG_VERBOSE=0
    cd tests/nightly/
    ../../tools/launch.py -n 7 --launcher local python dist_device_sync_kvstore.py
    ../../tools/launch.py -n 7 --launcher local python dist_sync_kvstore.py --type=init_gpu
}

test_ubuntu_cpu_python2() {
    set -ex
    pushd .
    export MXNET_LIBRARY_PATH=/work/build/libmxnet.so

    VENV=mxnet_py2_venv
    virtualenv -p `which python2` $VENV
    source $VENV/bin/activate
    pip install nose nose-timer

    cd /work/mxnet/python
    pip install -e .
    cd /work/mxnet
    python -m "nose" $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --verbose tests/python/unittest
    popd
}

test_ubuntu_cpu_python3() {
    set -ex
    pushd .
    export MXNET_LIBRARY_PATH=/work/build/libmxnet.so
    VENV=mxnet_py3_venv
    virtualenv -p `which python3` $VENV
    source $VENV/bin/activate

    cd /work/mxnet/python
    pip3 install nose nose-timer
    pip3 install -e .
    cd /work/mxnet
    python3 -m "nose" $NOSE_COVERAGE_ARGUMENTS $NOSE_TIMER_ARGUMENTS --verbose tests/python/unittest

    popd
}

build_static_python() {
    set -ex
    pushd .
    build_ccache_wrappers
    ./ci/publish/python/build.sh ${1}
    popd
}

package_static_python() {
    set -ex
    pushd .
    ./ci/publish/python/package.sh ${1}
    popd
}

##############################################################
# MAIN
#
# Run function passed as argument
set +x
if [ $# -gt 0 ]
then
    $@
else
    cat<<EOF

$0: Execute a function by passing it as an argument to the script:

Possible commands:

EOF
    declare -F | cut -d' ' -f3
    echo
fi
