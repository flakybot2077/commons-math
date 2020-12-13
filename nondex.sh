#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

COMMIT_RANGE=$(git rev-parse origin/master~1)...$(git rev-parse HEAD)
nondextests=$(git diff --name-status --diff-filter=AM $COMMIT_RANGE | grep /test/ | sed -e 's;.*test/java/;;' -e 's/.java//' -e 's;/;.;g')

if [ ! -z $nondextests ]
then
    printf "Running NonDex on tests:\n$nondextests\n"
    nondextests=$(echo $nondextests | tr -s '[:blank:]' ',')
    git clone --depth 1 --branch surefire-npe-error-fix https://github.com/mojilin/NonDex.git
    cd NonDex
    mvn install -DskipTests
    cd ..
    mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn edu.illinois:nondex-maven-plugin:1.1.3-SNAPSHOT:nondex -DnondexSeed=$(shuf -i 0-65000 -n 1) -DnondexRuns=10 -DfailIfNoTests=false -Dtest=$nondextests
fi  
if [ -d ".nondex" ]
then
    flakyTests=$(awk ' !x[$0]++' */.nondex/*/failures)
fi
if [ ! -z "$flakyTests" ]
then 
    printf "Found flaky tests:\n$flakyTests\n"
    exit 1 ;
else 
    printf "No flaky tests found.\n"
fi