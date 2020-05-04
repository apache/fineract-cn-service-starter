/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.cn.test.servicestarter;

import org.apache.fineract.cn.api.util.ApiFactory;
import org.apache.fineract.cn.test.env.ExtraProperties;
import org.apache.fineract.cn.test.env.TestEnvironment;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.rules.ExternalResource;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Myrle Krantz
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Microservice<T> extends ExternalResource {

  private static final long MAX_WAIT_DEFAULT = 150;

  private final Class<T> clazz;
  private final String artifactName;
  private final String artifactVersion;
  final TestEnvironment processEnvironment;
  final IntegrationTestEnvironment integrationTestEnvironment;
  final String applicationName;
  private ApiFactory apiFactory;
  private Process process;
  private T api;
  private Integer debuggingPort = null;
  private boolean debugSuspend = false;
  private long maxWait = MAX_WAIT_DEFAULT;

  public Microservice(
          final Class<T> clazz,
          final String artifactName,
          final String artifactVersion,
          final IntegrationTestEnvironment integrationTestEnvironment) {
    this.clazz = clazz;
    this.artifactName = artifactName;
    this.artifactVersion = artifactVersion;
    applicationName = AnnotatedElementUtils.getMergedAnnotationAttributes(clazz, FeignClient.class).getString("value");
    this.processEnvironment = new TestEnvironment(applicationName);
    processEnvironment.setProperty(TestEnvironment.SERVER_PORT_PROPERTY, integrationTestEnvironment.getFreshPort().toString());
    processEnvironment.setKeyPair(integrationTestEnvironment.getSeshatKeyTimestamp(), integrationTestEnvironment.getSeshatPublicKey(), integrationTestEnvironment.getSeshatPrivateKey());

    //https://github.com/spring-cloud/spring-cloud-netflix/issues/373
    //http://blog.abhijitsarkar.org/technical/netflix-eureka/
    processEnvironment.setProperty("eureka.client.serviceUrl.defaultZone", EurekaForTest.DEFAULT_ZONE);
    processEnvironment.setProperty(TestEnvironment.SPRING_CLOUD_DISCOVERY_ENABLED_PROPERTY, "true");
    processEnvironment.setProperty("eureka.instance.hostname", "localhost");
    processEnvironment.setProperty("eureka.client.fetchRegistry", "true");
    processEnvironment.setProperty("eureka.registration.enabled", "true");
    processEnvironment.setProperty("eureka.instance.leaseRenewalIntervalInSeconds", "1"); //Speed up registration for test purposes.
    processEnvironment.setProperty("eureka.client.initialInstanceInfoReplicationIntervalSeconds", "0");  //Speed up initial registration for test purposes.
    processEnvironment.setProperty("eureka.client.instanceInfoReplicationIntervalSeconds", "1");
    processEnvironment.setProperty("activemq.brokerUrl", ActiveMQForTest.BIND_ADDRESS);

    processEnvironment.setProperty(TestEnvironment.RIBBON_USES_EUREKA_PROPERTY, "true");

    this.integrationTestEnvironment = integrationTestEnvironment;
  }

  public Microservice<T> addProperties(final ExtraProperties properties) {
    properties.entrySet().forEach(x -> this.processEnvironment.setProperty(x.getKey(), x.getValue()));

    return this;
  }

  public Microservice<T> debug(boolean suspend, int port) {
    this.debuggingPort = port;
    this.debugSuspend = suspend;

    return this;
  }

  public Microservice<T> runInDebug() {
    this.debuggingPort = integrationTestEnvironment.getFreshDebugPort();
    this.debugSuspend = false;

    return this;
  }

  public Microservice<T> maxWait(final long maxWait) {
    this.maxWait = maxWait;
    return this;
  }

  public Microservice<T> setApiFactory(final ApiFactory newValue)
  {
    this.apiFactory = newValue;
    return this;
  }

  public boolean waitTillRegistered(final DiscoveryClient discoveryClient) throws InterruptedException {
    if (discoveryClient == null) {
      return false;
    }

    long nextWait = 1;
    long sumWait = 0;

    boolean found = false;

    while (!found) {
      final List<ServiceInstance> thisApp = discoveryClient.getInstances(applicationName);
      found = !thisApp.isEmpty();
      if (!found) {
        TimeUnit.SECONDS.sleep(nextWait);
        sumWait += nextWait;
        nextWait = nextWait * 2;

        if (sumWait > maxWait) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  protected void before() throws InterruptedException, IOException, ArtifactResolutionException {
    start();
  }

  @Override
  protected void after() {
    try {
      kill();
    } catch (final InterruptedException e) {
      System.out.println("Interrupt raised, but microservice is already going down, so ignoring.");
    }
  }

  public void start() throws ArtifactResolutionException, IOException, InterruptedException {

    final ArtifactResolver artifactResolver = new ArtifactResolver(
            IntegrationTestEnvironment.getArtifactoryDirectory());

    final File jarFile = artifactResolver.getJarFile(artifactName, "org.apache.fineract.cn." + artifactName, "service-boot", artifactVersion);

    final ProcessBuilder processBuilder;
    if (debuggingPort == null) {
      processBuilder = new ProcessBuilder(IntegrationTestEnvironment.getJava(), "-jar", jarFile.getAbsolutePath());
    }
    else {
      processBuilder = new ProcessBuilder(IntegrationTestEnvironment.getJava(), "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (debugSuspend ? "y" : "n") + ",address=" + debuggingPort,
              "-jar", jarFile.getAbsolutePath());
    }
    processEnvironment.populateProcessEnvironment(processBuilder);
    processBuilder.inheritIO();

    process = processBuilder.start();
  }

  public int kill() throws InterruptedException {
    process.destroy();
    process.waitFor();
    return process.exitValue();
  }

  public T api() {
    if (this.api == null) {
      this.api = this.apiFactory.create(clazz, processEnvironment.serverURI());
    }
    return this.api;
  }

  public String uri() {
    return processEnvironment.serverURI();
  }

  public TestEnvironment getProcessEnvironment()
  {
    return processEnvironment;
  }

  public String name() {
    return applicationName;
  }

  public Integer debuggingPort() {
    return debuggingPort;
  }

  public String toString() {
    final StringBuilder ret = new StringBuilder(applicationName);
    ret.append(" address:");
    ret.append(this.getProcessEnvironment().serverURI());

    if (debuggingPort != null) {
      ret.append(", debuggingPort: ");
      ret.append(debuggingPort);
    }

    return ret.toString();
  }
}
