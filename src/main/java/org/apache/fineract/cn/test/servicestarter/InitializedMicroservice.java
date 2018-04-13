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

import java.io.IOException;
import org.apache.fineract.cn.anubis.test.v1.TenantApplicationSecurityEnvironmentTestRule;
import org.apache.fineract.cn.test.env.ExtraProperties;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * @author Myrle Krantz
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class InitializedMicroservice<T> extends Microservice<T> {
  public InitializedMicroservice(
          final Class<T> clazz,
          final String artifactName,
          final String artifactVersion,
          final IntegrationTestEnvironment integrationTestEnvironment) {
    super(clazz, artifactName, artifactVersion, integrationTestEnvironment);
    this.integrationTestEnvironment.addApplication(applicationName);
  }

  @Override
  public InitializedMicroservice<T> addProperties(final ExtraProperties properties) {
    super.addProperties(properties);
    return this;
  }

  @Override
  public InitializedMicroservice<T> debug(boolean suspend, int port) {
    super.debug(suspend, port);
    return this;
  }

  public void start() throws InterruptedException, IOException, ArtifactResolutionException {
    super.start();
    final TenantApplicationSecurityEnvironmentTestRule tenantApplicationSecurityEnvironment
            = new TenantApplicationSecurityEnvironmentTestRule(applicationName, processEnvironment.serverURI(), integrationTestEnvironment.getSystemSecurityEnvironment());
    tenantApplicationSecurityEnvironment.initializeTenantInApplication();
  }
}
