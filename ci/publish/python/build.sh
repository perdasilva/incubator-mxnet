#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -ex

mxnet_variant=${1}
source tools/staticbuild/build.sh ${mxnet_variant} pip

set -ex

# Build wheel file in its own directory
# symlink the mxnet directory (with compiled artifacts)
# to mxnet-build
rm -rf wheel_build
mkdir wheel_build
cd wheep_build

cp -R ../tools/pip/* .

# setup.py expects 
ln -s ../mxnet mxnet-build
python setup.py bdist_wheel
