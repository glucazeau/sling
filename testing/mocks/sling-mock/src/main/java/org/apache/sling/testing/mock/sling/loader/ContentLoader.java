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
package org.apache.sling.testing.mock.sling.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.fscontentparser.ContentFileParser;
import org.apache.sling.fscontentparser.ContentFileParserFactory;
import org.apache.sling.fscontentparser.ContentFileType;
import org.apache.sling.fscontentparser.ParseException;
import org.apache.sling.fscontentparser.ParserOptions;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Imports JSON data and binary data into Sling resource hierarchy.
 * After all import operations from json or binaries {@link ResourceResolver#commit()} is called (when autocommit mode is active).
 */
public final class ContentLoader {

    private static final String CONTENTTYPE_OCTET_STREAM = "application/octet-stream";
    private static final String JCR_DATA_PLACEHOLDER = ":jcr:data";

    private static final Set<String> IGNORED_NAMES = ImmutableSet.of(
            JcrConstants.JCR_MIXINTYPES,
            JcrConstants.JCR_UUID,
            JcrConstants.JCR_BASEVERSION,
            JcrConstants.JCR_PREDECESSORS,
            JcrConstants.JCR_SUCCESSORS,
            JcrConstants.JCR_CREATED,
            JcrConstants.JCR_VERSIONHISTORY,
            "jcr:checkedOut",
            "jcr:isCheckedOut",
            "rep:policy");
    
    private static ContentFileParser JSON_PARSER = ContentFileParserFactory.create(ContentFileType.JSON, new ParserOptions()
            .detectCalendarValues(true)
            .ignorePropertyNames(IGNORED_NAMES)
            .ignoreResourceNames(IGNORED_NAMES));

    private final ResourceResolver resourceResolver;
    private final BundleContext bundleContext;
    private final boolean autoCommit;

    /**
     * @param resourceResolver Resource resolver
     */
    public ContentLoader(ResourceResolver resourceResolver) {
        this(resourceResolver, null);
    }

    /**
     * @param resourceResolver Resource resolver
     * @param bundleContext Bundle context
     */
    public ContentLoader(ResourceResolver resourceResolver, BundleContext bundleContext) {
        this (resourceResolver, bundleContext, true);
    }

    /**
     * @param resourceResolver Resource resolver
     * @param bundleContext Bundle context
     * @param autoCommit Automatically commit changes after loading content (default: true)
     */
    public ContentLoader(ResourceResolver resourceResolver, BundleContext bundleContext, boolean autoCommit) {
        this.resourceResolver = resourceResolver;
        this.bundleContext = bundleContext;
        this.autoCommit = autoCommit;
    }

    /**
     * Import content of JSON file into repository.
     * @param classpathResource Classpath resource URL for JSON content
     * @param parentResource Parent resource
     * @param childName Name of child resource to create with JSON content
     * @return Resource
     */
    public Resource json(String classpathResource, Resource parentResource, String childName) {
        InputStream is = ContentLoader.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        try {
            return json(is, parentResource, childName);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Import content of JSON file into repository. Auto-creates parent
     * hierarchies as nt:unstrucured nodes if missing.
     * @param classpathResource Classpath resource URL for JSON content
     * @param destPath Path to import the JSON content to
     * @return Resource
     */
    public Resource json(String classpathResource, String destPath) {
        InputStream is = ContentLoader.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        try {
            return json(is, destPath);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Import content of JSON file into repository.
     * @param inputStream Input stream with JSON content
     * @param parentResource Parent resource
     * @param childName Name of child resource to create with JSON content
     * @return Resource
     */
    public Resource json(InputStream inputStream, Resource parentResource, String childName) {
        return json(inputStream, parentResource.getPath() + "/" + childName);
    }

    /**
     * Import content of JSON file into repository. Auto-creates parent
     * hierarchies as nt:unstrucured nodes if missing.
     * @param inputStream Input stream with JSON content
     * @param destPath Path to import the JSON content to
     * @return Resource
     */
    public Resource json(InputStream inputStream, String destPath) {
        try {
            String parentPath = ResourceUtil.getParent(destPath);
            String childName = ResourceUtil.getName(destPath);

            Resource parentResource = resourceResolver.getResource(parentPath);
            if (parentResource == null) {
                parentResource = createResourceHierarchy(parentPath);
            }
            if (parentResource.getChild(childName) != null) {
                throw new IllegalArgumentException("Resource does already exist: " + destPath);
            }

            Map<String,Object> content = JSON_PARSER.parse(inputStream);
            Resource resource = this.createResource(parentResource, childName, content);
            if (autoCommit) {
                resourceResolver.commit();
            }
            return resource;
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Resource createResourceHierarchy(String path) {
        String parentPath = ResourceUtil.getParent(path);
        if (parentPath == null) {
            return null;
        }
        Resource parentResource = resourceResolver.getResource(parentPath);
        if (parentResource == null) {
            parentResource = createResourceHierarchy(parentPath);
        }
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED);
        try {
            return resourceResolver.create(parentResource, ResourceUtil.getName(path), props);
        } catch (PersistenceException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Resource createResource(Resource parentResource, String childName, Map<String,Object> content) throws IOException {
        
        // collect all properties first
        boolean hasJcrData = false;
        Map<String, Object> props = new HashMap<String, Object>();
        for (Map.Entry<String,Object> entry : content.entrySet()) {
            final String name = entry.getKey();
            if (StringUtils.equals(name, JCR_DATA_PLACEHOLDER)) {
                hasJcrData = true;
            }
            else if (!(entry.getValue() instanceof Map)) {
                props.put(name, entry.getValue());
            }
        }
        
        // create resource
        Resource resource = resourceResolver.create(parentResource, childName, props);
        
        if (hasJcrData) {
            ModifiableValueMap valueMap = resource.adaptTo(ModifiableValueMap.class);
            // we cannot import binary data here - but to avoid complaints by JCR we create it with empty binary data
            valueMap.put(JcrConstants.JCR_DATA, new ByteArrayInputStream(new byte[0]));
        }

        // add child resources
        for (Map.Entry<String,Object> entry : content.entrySet()) {
            if (entry.getValue() instanceof Map) {
                createResource(resource, entry.getKey(), (Map<String,Object>)entry.getValue());
            }
        }

        return resource;
    }

    /**
     * Import binary file as nt:file binary node into repository. Auto-creates
     * parent hierarchies as nt:unstrucured nodes if missing. Mime type is
     * auto-detected from resource name.
     * @param classpathResource Classpath resource URL for binary file.
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @return Resource with binary data
     */
    public Resource binaryFile(String classpathResource, String path) {
        InputStream is = ContentLoader.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        try {
            return binaryFile(is, path, detectMimeTypeFromName(path));
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Import binary file as nt:file binary node into repository. Auto-creates
     * parent hierarchies as nt:unstrucured nodes if missing.
     * @param classpathResource Classpath resource URL for binary file.
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @param mimeType Mime type of binary data
     * @return Resource with binary data
     */
    public Resource binaryFile(String classpathResource, String path, String mimeType) {
        InputStream is = ContentLoader.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        try {
            return binaryFile(is, path, mimeType);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Import binary file as nt:file binary node into repository. Auto-creates
     * parent hierarchies as nt:unstrucured nodes if missing. Mime type is
     * auto-detected from resource name.
     * @param inputStream Input stream for binary data
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @return Resource with binary data
     */
    public Resource binaryFile(InputStream inputStream, String path) {
        return binaryFile(inputStream, path, detectMimeTypeFromName(path));
    }

    /**
     * Import binary file as nt:file binary node into repository. Auto-creates
     * parent hierarchies as nt:unstrucured nodes if missing.
     * @param inputStream Input stream for binary data
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @param mimeType Mime type of binary data
     * @return Resource with binary data
     */
    public Resource binaryFile(InputStream inputStream, String path, String mimeType) {
        String parentPath = ResourceUtil.getParent(path, 1);
        String name = ResourceUtil.getName(path);
        Resource parentResource = resourceResolver.getResource(parentPath);
        if (parentResource == null) {
            parentResource = createResourceHierarchy(parentPath);
        }
        return binaryFile(inputStream, parentResource, name, mimeType);
    }

    /**
     * Import binary file as nt:file binary node into repository. Auto-creates
     * parent hierarchies as nt:unstrucured nodes if missing. Mime type is
     * auto-detected from resource name.
     * @param inputStream Input stream for binary data
     * @param parentResource Parent resource
     * @param name Resource name for nt:file
     * @return Resource with binary data
     */
    public Resource binaryFile(InputStream inputStream, Resource parentResource, String name) {
        return binaryFile(inputStream, parentResource, name, detectMimeTypeFromName(name));
    }

    /**
     * Import binary file as nt:file binary node into repository. Auto-creates
     * parent hierarchies as nt:unstrucured nodes if missing.
     * @param inputStream Input stream for binary data
     * @param parentResource Parent resource
     * @param name Resource name for nt:file
     * @param mimeType Mime type of binary data
     * @return Resource with binary data
     */
    public Resource binaryFile(InputStream inputStream, Resource parentResource, String name, String mimeType) {
        try {
            Resource file = resourceResolver.create(parentResource, name,
                    ImmutableMap.<String, Object> builder().put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE)
                            .build());
            resourceResolver.create(file, JcrConstants.JCR_CONTENT,
                    ImmutableMap.<String, Object> builder().put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_RESOURCE)
                            .put(JcrConstants.JCR_DATA, inputStream).put(JcrConstants.JCR_MIMETYPE, mimeType).build());
            if (autoCommit) {
                resourceResolver.commit();
            }
            return file;
        } catch (PersistenceException ex) {
            throw new RuntimeException("Unable to create resource at " + parentResource.getPath() + "/" + name, ex);
        }
    }

    /**
     * Import binary file as nt:resource binary node into repository.
     * Auto-creates parent hierarchies as nt:unstrucured nodes if missing. Mime
     * type is auto-detected from resource name.
     * @param classpathResource Classpath resource URL for binary file.
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @return Resource with binary data
     */
    public Resource binaryResource(String classpathResource, String path) {
        InputStream is = ContentLoader.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        try {
            return binaryResource(is, path, detectMimeTypeFromName(path));
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Import binary file as nt:resource binary node into repository.
     * Auto-creates parent hierarchies as nt:unstrucured nodes if missing.
     * @param classpathResource Classpath resource URL for binary file.
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @param mimeType Mime type of binary data
     * @return Resource with binary data
     */
    public Resource binaryResource(String classpathResource, String path, String mimeType) {
        InputStream is = ContentLoader.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        try {
            return binaryResource(is, path, mimeType);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Import binary file as nt:resource binary node into repository.
     * Auto-creates parent hierarchies as nt:unstrucured nodes if missing. Mime
     * type is auto-detected from resource name.
     * @param inputStream Input stream for binary data
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @return Resource with binary data
     */
    public Resource binaryResource(InputStream inputStream, String path) {
        return binaryResource(inputStream, path, detectMimeTypeFromName(path));
    }

    /**
     * Import binary file as nt:resource binary node into repository.
     * Auto-creates parent hierarchies as nt:unstrucured nodes if missing.
     * @param inputStream Input stream for binary data
     * @param path Path to mount binary data to (parent nodes created
     *            automatically)
     * @param mimeType Mime type of binary data
     * @return Resource with binary data
     */
    public Resource binaryResource(InputStream inputStream, String path, String mimeType) {
        String parentPath = ResourceUtil.getParent(path, 1);
        String name = ResourceUtil.getName(path);
        Resource parentResource = resourceResolver.getResource(parentPath);
        if (parentResource == null) {
            parentResource = createResourceHierarchy(parentPath);
        }
        return binaryResource(inputStream, parentResource, name, mimeType);
    }

    /**
     * Import binary file as nt:resource binary node into repository.
     * Auto-creates parent hierarchies as nt:unstrucured nodes if missing. Mime
     * type is auto-detected from resource name.
     * @param inputStream Input stream for binary data
     * @param parentResource Parent resource
     * @param name Resource name for nt:resource
     * @return Resource with binary data
     */
    public Resource binaryResource(InputStream inputStream, Resource parentResource, String name) {
        return binaryResource(inputStream, parentResource, name, detectMimeTypeFromName(name));
    }

    /**
     * Import binary file as nt:resource binary node into repository.
     * Auto-creates parent hierarchies as nt:unstrucured nodes if missing.
     * @param inputStream Input stream for binary data
     * @param parentResource Parent resource
     * @param name Resource name for nt:resource
     * @param mimeType Mime type of binary data
     * @return Resource with binary data
     */
    public Resource binaryResource(InputStream inputStream, Resource parentResource, String name, String mimeType) {
        try {
            Resource resource = resourceResolver.create(parentResource, name,
                    ImmutableMap.<String, Object> builder().put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_RESOURCE)
                            .put(JcrConstants.JCR_DATA, inputStream).put(JcrConstants.JCR_MIMETYPE, mimeType).build());
            if (autoCommit) {
                resourceResolver.commit();
            }
            return resource;
        } catch (PersistenceException ex) {
            throw new RuntimeException("Unable to create resource at " + parentResource.getPath() + "/" + name, ex);
        }
    }

    /**
     * Detected mime type from name (file extension) using Mime Type service.
     * Fallback to application/octet-stream.
     * @param name Node name
     * @return Mime type (never null)
     */
    private String detectMimeTypeFromName(String name) {
        String mimeType = null;
        String fileExtension = StringUtils.substringAfterLast(name, ".");
        if (bundleContext != null && StringUtils.isNotEmpty(fileExtension)) {
            ServiceReference<MimeTypeService> ref = bundleContext.getServiceReference(MimeTypeService.class);
            if (ref != null) {
                MimeTypeService mimeTypeService = (MimeTypeService)bundleContext.getService(ref);
                mimeType = mimeTypeService.getMimeType(fileExtension);
            }
        }
        return StringUtils.defaultString(mimeType, CONTENTTYPE_OCTET_STREAM);
    }

}
