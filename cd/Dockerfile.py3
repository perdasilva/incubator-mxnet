# -*- mode: dockerfile -*-
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
# Python3 MXNet Dockerfile
ARG PLATFORM
FROM ${PLATFORM}

RUN apt-get update && \
    apt-get install -y wget python3-dev gcc && \
    wget https://bootstrap.pypa.io/get-pip.py && \
    python3 get-pip.py

RUN mkdir -p /mxnet
COPY *.whl /mxnet/.

WORKDIR /mxnet
RUN WHEEL_FILE=$(ls -t /mxnet | head -n 1) && echo ${WHEEL_FILE} && pip3 install ${WHEEL_FILE} && rm -f ${WHEEL_FILE}

