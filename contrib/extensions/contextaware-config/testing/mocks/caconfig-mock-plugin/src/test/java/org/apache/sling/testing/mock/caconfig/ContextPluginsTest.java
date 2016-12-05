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
package org.apache.sling.testing.mock.caconfig;

import static org.apache.sling.testing.mock.caconfig.ContextPlugins.CACONFIG;
import static org.junit.Assert.assertEquals;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.apache.sling.caconfig.management.ConfigurationManager;
import org.apache.sling.caconfig.spi.ConfigurationPersistData;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.junit.SlingContextBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class ContextPluginsTest {
    
    private static final String CONFIG_NAME = "testConfig";
    
    @Rule
    public SlingContext context = new SlingContextBuilder().plugin(CACONFIG).build();
    
    private Resource contextResource;

    @Before
    public void setUp() {
        context.create().resource("/content/site1", "sling:configRef", "/conf/site1");
        contextResource = context.create().resource("/content/site1/page1");
    }
    
    @Test
    public void testPlugin() {
        
        // write config
        ConfigurationManager configManager = context.getService(ConfigurationManager.class);
        configManager.persistConfiguration(contextResource, CONFIG_NAME, 
                new ConfigurationPersistData(ImmutableMap.<String, Object>of(
                        "prop1", "value1",
                        "prop2", 123)));
        
        // read config
        ValueMap props = contextResource.adaptTo(ConfigurationBuilder.class).name(CONFIG_NAME).asValueMap();
        assertEquals("value1", props.get("prop1", String.class));
        assertEquals((Integer)123, props.get("prop2", Integer.class));
    }

}