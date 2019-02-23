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

# variant = cpu, mkl, cu80, cu80mkl, cu100, etc.
export mxnet_variant=${1}

# Create wheel workspace
rm -rf wheel_build
mkdir wheel_build
cd wheel_build

# Setup workspace
# setup.py expects mxnet-build to be the
# mxnet directory
ln -s ../. mxnet-build

# Copy the setup.py and other package resources
cp -R ../tools/pip/* .

# Build wheel file - placed in wheel_build/dist
python setup.py bdist_wheel
