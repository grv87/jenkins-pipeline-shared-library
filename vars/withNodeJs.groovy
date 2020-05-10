#!/usr/bin/env groovy
/*
 * withNodeJs Jenkins Pipeline step
 * Copyright ©  Basil Peace
 *
 * This file is part of jenkins-pipeline-shared-library.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.util.regex.Matcher
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.Version
import com.github.zafarkhaja.semver.ParseException
import hudson.AbortException

/**
 * Gets NodeJS version as String, e.g. {@code 1.2.3}
 * @return NodeJS version
 */
String getNodeJsVersion() {
  final String nodeJsVersionOutput = exec('node --version', true)
  echo nodeJsVersionOutput
  (nodeJsVersionOutput =~ /^v(\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

void call(String artifactoryServerId, Closure body) {
  final ArtifactoryServer server = Artifactory.server(artifactoryServerId)
  final URL url = new URL(server.url)

  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
  echo 'Determining installed Node.js version...'
  lock('node --version') { ->
    Boolean isNodeJsInstalled
    try {
      isNodeJsInstalled = Version.valueOf(getNodeJsVersion())?.greaterThanOrEqualTo(Version.forIntegers(10, 0, 0))
    } catch (AbortException | IllegalArgumentException | ParseException ignored) {
      isNodeJsInstalled = false
    }
    if (!isNodeJsInstalled) {
      echo 'Installing recent Node.js version...'
      if (isUnix()) {
        sh '''\
          curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -
          sudo apt-get --assume-yes install nodejs
        '''.stripIndent()
      } else {
        throw new UnsupportedOperationException('Installation of Node.js under Windows is not supported yet')
      }
    }
  }

  withEnv([
    "npm_config_registry=$url/api/npm/npm/",
    "npm_config_@fidata:registry=$url/api/npm/npm-local/",
    "npm_config_@sourcemetadata:registry=$url/api/npm/npm-local/",
    "npm_config_//$url.host$url.path/api/npm/npm-local/:email=jenkins@fidata.org", // TODO
    "npm_config_//$url.host$url.path/api/npm/npm-local/:always-auth=true",
    "npm_config_//$url.host$url.path/api/npm/npm/:email=jenkins@fidata.org",
    "npm_config_//$url.host$url.path/api/npm/npm/:always-auth=true",
  ]) { ->
    withSecretEnv([
      [var: "npm_config_//$url.host$url.path/api/npm/npm-local/:username", password: server.username],
      [var: "npm_config_//$url.host$url.path/api/npm/npm-local/:_password", password: server.password.bytes.encodeBase64().toString()],
      [var: "npm_config_//$url.host$url.path/api/npm/npm/:username", password: server.username],
      [var: "npm_config_//$url.host$url.path/api/npm/npm/:_password", password: server.password.bytes.encodeBase64().toString()],
    ]) { ->
      body.call()
    }
  }
}
