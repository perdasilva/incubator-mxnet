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

ci_utils = load('ci/Jenkinsfile_utils.groovy')
cd_utils = load('ci/cd/Jenkinsfile_utils.groovy')

// mxnet libraries
mx_lib = 'lib/libmxnet.so, lib/libmxnet.a, 3rdparty/dmlc-core/libdmlc.a, 3rdparty/tvm/nnvm/lib/libnnvm.a'

// Python wheels
mx_pip = 'build/*.whl'

// mxnet cmake libraries, in cmake builds we do not produce a libnvvm static library by default.
mx_cmake_lib = 'build/libmxnet.so, build/libmxnet.a, build/3rdparty/dmlc-core/libdmlc.a, build/tests/mxnet_unit_tests, build/3rdparty/openmp/runtime/src/libomp.so'
// mxnet cmake libraries, in cmake builds we do not produce a libnvvm static library by default.
mx_cmake_lib_debug = 'build/libmxnet.so, build/libmxnet.a, build/3rdparty/dmlc-core/libdmlc.a, build/tests/mxnet_unit_tests'
mx_cmake_mkldnn_lib = 'build/libmxnet.so, build/libmxnet.a, build/3rdparty/dmlc-core/libdmlc.a, build/tests/mxnet_unit_tests, build/3rdparty/openmp/runtime/src/libomp.so, build/3rdparty/mkldnn/src/libmkldnn.so.0'
mx_mkldnn_lib = 'lib/libmxnet.so, lib/libmxnet.a, lib/libiomp5.so, lib/libmkldnn.so.0, lib/libmklml_intel.so, 3rdparty/dmlc-core/libdmlc.a, 3rdparty/tvm/nnvm/lib/libnnvm.a'
mx_tensorrt_lib = 'build/libmxnet.so, lib/libnvonnxparser_runtime.so.0, lib/libnvonnxparser.so.0, lib/libonnx_proto.so, lib/libonnx.so'
mx_lib_cpp_examples = 'lib/libmxnet.so, lib/libmxnet.a, 3rdparty/dmlc-core/libdmlc.a, 3rdparty/tvm/nnvm/lib/libnnvm.a, 3rdparty/ps-lite/build/libps.a, deps/lib/libprotobuf-lite.a, deps/lib/libzmq.a, build/cpp-package/example/*'
mx_lib_cpp_examples_cpu = 'build/libmxnet.so, build/cpp-package/example/*'

def compile_dynamic_unix_cpu_openblas() {
    return ['CPU: Openblas': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-dynamic-cpu-openblas') {
          timeout(time: max_time, unit: 'MINUTES') {
            ci_utils.init_git()
            cd_utils.docker_run('publish.ubuntu1404_cpu', 'compile_dynamic_unix_cpu_openblas', false)
            ci_utils.pack_lib('cpu', mx_lib, true)
          }
        }
      }
    }]
}

def compile_static_unix_cpu_openblas() {
    return ['CPU: Openblas': {
      node(NODE_LINUX_CPU) {
        ws('workspace/build-static-cpu-openblas') {
          timeout(time: max_time, unit: 'MINUTES') {
            ci_utils.init_git()
            cd_utils.docker_run('publish.ubuntu1404_cpu', 'compile_static_unix_cpu_openblas', false)
            ci_utils.pack_lib('cpu', mx_lib, true)
          }
        }
      }
    }]
}

return this
