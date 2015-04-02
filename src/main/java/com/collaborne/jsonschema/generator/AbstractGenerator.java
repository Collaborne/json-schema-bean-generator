/**
 * Copyright (C) 2015 Collaborne B.V. (opensource@collaborne.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.collaborne.jsonschema.generator;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.model.Mapping;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.core.load.SchemaLoader;

public abstract class AbstractGenerator implements Generator {
	private final Logger logger = LoggerFactory.getLogger(AbstractGenerator.class);

	private final Map<String, Object> features = new HashMap<>();
	private Map<URI, Mapping> mappings = new HashMap<>();
	private Map<URI, String> defaultPackageNames = new HashMap<>();
	private Path outputDirectory;
	private SchemaLoader schemaLoader;

	@Override
	public <T>T getFeature(Feature<T> feature) {
		return feature.get(features);
	}
	
	@Override
	public <T>T setFeature(Feature<T> feature, T value) {
		return feature.set(features, value);
	}

	@Override
	public void addMapping(URI type, Mapping mapping) {
		if (!type.isAbsolute()) {
			logger.warn("{}: Adding mapping for non-absolute type", type);
		}
		mappings.put(type, mapping);
	}

	@Override
	public void addDefaultPackageName(URI baseUri, String packageName) {
		URI packageUri;
		if (baseUri.getFragment() == null) {
			packageUri = baseUri.resolve("#");
		} else {
			packageUri = baseUri;
		}
		defaultPackageNames.put(packageUri, packageName);
	}

	/**
	 * Get the default package name for the given type.
	 *
	 * @param type
	 * @return
	 */
	public String getDefaultPackageName(URI type) {
		URI searchUri;
		if (type.getFragment() == null) {
			searchUri = type.resolve("#");
		} else {
			searchUri = type;
		}

		String defaultPackageName = defaultPackageNames.get(searchUri);
		while (defaultPackageName == null && !(searchUri.getFragment().isEmpty() && (searchUri.getPath().isEmpty() || "/".equals(searchUri.getPath())))) {
			try {
				if (!searchUri.getFragment().isEmpty()) {
					JsonPointer pointer = new JsonPointer(searchUri.getFragment());
					searchUri = searchUri.resolve("#" + pointer.parent().toString());
				} else {
					String path = searchUri.getPath();
					int lastSlashIndex = path.lastIndexOf('/');
					searchUri = searchUri.resolve(path.substring(0, lastSlashIndex)).resolve("#" + searchUri.getFragment());
				}

				defaultPackageName = defaultPackageNames.get(searchUri);
			} catch (JsonPointerException e) {
				// Assuming that the type was valid before we shouldn't end up here.
				// However, that's just half the truth: it is possible to ask for a type with an URI that has a fragment that
				// cannot be interpreted as a JsonPointer (which starts with '/').
				throw new IllegalArgumentException("Cannot construct JSON pointer", e);
			}
		}
		if (defaultPackageName == null) {
			defaultPackageName = getFeature(FEATURE_DEFAULT_PACKAGE_NAME);
			logger.warn("{}: Using package name {}", type, defaultPackageName);
		}
		return defaultPackageName;
	}

	protected Path getOutputDirectory() {
		return outputDirectory;
	}
	
	@Override
	public void setOutputDirectory(Path outputDirectory) {
		this.outputDirectory = outputDirectory;
	}
	
	@Override
	public void setSchemaLoader(SchemaLoader schemaLoader) {
		this.schemaLoader = schemaLoader;
	}
	
	protected SchemaLoader getSchemaLoader() {
		return schemaLoader;
	}
	
	/**
	 * Get an existing mapping for the given {@code type}.
	 * 
	 * If no mapping is known for this type, {@code null} is returned.
	 * 
	 * @param type
	 * @return
	 */
	protected Mapping getMapping(URI type) {
		return mappings.get(type);
	}
}
