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
package org.apache.sling.models.impl.injectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

import javax.servlet.http.HttpServletRequest;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.impl.model.ConstructorParameter;
import org.apache.sling.models.spi.injectorspecific.StaticInjectAnnotationProcessorFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SelfInjectorTest {

    private SelfInjector injector = new SelfInjector();

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private AnnotatedElement annotatedElement;
    
    private ConstructorParameter firstConstructorParameter;
    private ConstructorParameter secondConstructorParameter;
    
    @Before
    public void setup() {
        firstConstructorParameter = new ConstructorParameter(new Annotation[0], Object.class, Object.class, true, 0,
                new StaticInjectAnnotationProcessorFactory[0]);
        secondConstructorParameter = new ConstructorParameter(new Annotation[0], Object.class, Object.class, true, 1,
                new StaticInjectAnnotationProcessorFactory[0]);
    }

    @Test
    public void testMatchingClass() {
        assertSame(request, injector.getValue(request, "notRelevant", SlingHttpServletRequest.class, firstConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, "notRelevant", SlingHttpServletRequest.class, secondConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, "notRelevant", SlingHttpServletRequest.class, annotatedElement, null));
    }

    @Test
    public void testMatchingSubClass() {
        assertSame(request, injector.getValue(request, "notRelevant", HttpServletRequest.class, firstConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, "notRelevant", HttpServletRequest.class, secondConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, "notRelevant", HttpServletRequest.class, annotatedElement, null));
    }

    @Test
    public void testNotMatchingClass() {
        assertNull(injector.getValue(request, "notRelevant", ResourceResolver.class, firstConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, "notRelevant", ResourceResolver.class, secondConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, "notRelevant", ResourceResolver.class, annotatedElement, null));
    }

    @Test
    public void testWithNullName() {
        assertSame(request, injector.getValue(request, null, SlingHttpServletRequest.class, firstConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, null, SlingHttpServletRequest.class, secondConstructorParameter.getAnnotatedElement(), null));
        assertNull(injector.getValue(request, null, SlingHttpServletRequest.class, annotatedElement, null));
    }

    @Test
    public void testMatchingClassWithSelfAnnotation() {
        when(annotatedElement.isAnnotationPresent(Self.class)).thenReturn(true);
        Object result = injector
                .getValue(request, "notRelevant", SlingHttpServletRequest.class, annotatedElement, null);
        assertSame(request, result);
    }

    @Test
    public void testNotMatchingClassWithSelfAnnotation() {
        when(annotatedElement.isAnnotationPresent(Self.class)).thenReturn(true);
        Object result = injector.getValue(request, "notRelevant", ResourceResolver.class, annotatedElement, null);
        assertSame(request, result);
    }

}
