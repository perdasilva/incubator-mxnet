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

CI_CUDA_COMPUTE_CAPABILITIES="-gencode=arch=compute_52,code=sm_52 -gencode=arch=compute_70,code=sm_70"

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

compile_dynamic_unix_cpu_openblas() {
    set -ex
    # export CC="gcc"
    # export CXX="g++"
    # build_ccache_wrappers
    # cp make/cd_dynamic/cd_dynamic_linux_cpu.mk config.mk
    # make -j$(nproc)
    source tools/staticbuild/build.sh cpu cd_dynamic

    # export CC="gcc"
    # export CXX="g++"
    # build_ccache_wrappers
    # make \
    #     DEV=0                         \
    #     ENABLE_TESTCOVERAGE=1         \
    #     USE_CPP_PACKAGE=0             \
    #     USE_BLAS=openblas             \
    #     USE_MKLDNN=0                  \
    #     USE_DIST_KVSTORE=1            \
    #     USE_LIBJPEG_TURBO=1           
    #     -j$(nproc)
}

compile_static_unix_cpu_openblas() {
    set -ex
    source tools/staticbuild/build.sh cpu cd_static
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
