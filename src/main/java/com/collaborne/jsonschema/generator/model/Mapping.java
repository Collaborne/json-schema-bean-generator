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
package com.collaborne.jsonschema.generator.model;

import java.net.URI;
import java.util.List;

import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.Modifier;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

// XXX: should be generated by the generator?
// TODO: need "inject header" (for licenses etc)
// TODO: decide whether className is FQCN or we have a #getPackage() as well
public class Mapping {
	private URI target;
	/** Name of the class to be used when referencing this type */
	private ClassName className;
	/** Name of the class to be generated, if different from {@link #className}. */
	private ClassName generatedClassName;
	private ClassName extendedClass;
	private List<ClassName> implementedInterfaces;
	private boolean ignoreAdditionalProperties;
	private List<Modifier> modifiers;

	public Mapping() {
		// For jackson
	}

	public Mapping(URI target, ClassName className) {
		this.target = target;
		this.className = className;
	}

	public URI getTarget() {
		return target;
	}

	public void setTarget(URI target) {
		this.target = target;
	}

	@JsonDeserialize(converter=ClassNameConverter.class)
	public ClassName getClassName() {
		return className;
	}

	public void setClassName(ClassName className) {
		this.className = className;
	}

	@JsonDeserialize(converter=ClassNameConverter.class)
	public ClassName getGeneratedClassName() {
		// If the user didn't specify a name for the class to be generated explicitly,
		// use the one they specified as class name.
		if (generatedClassName != null) {
			return generatedClassName;
		}
		return getClassName();
	}

	public void setGeneratedClassName(ClassName generatedClassName) {
		this.generatedClassName = generatedClassName;
	}

	public ClassName getExtends() {
		return extendedClass;
	}

	@JsonDeserialize(converter=ClassNameConverter.class)
	public void setExtends(ClassName extendedClass) {
		this.extendedClass = extendedClass;
	}

	public List<ClassName> getImplements() {
		return implementedInterfaces;
	}

	@JsonDeserialize(contentConverter=ClassNameConverter.class)
	public void setImplements(List<ClassName> implementedInterfaces) {
		this.implementedInterfaces = implementedInterfaces;
	}

	public boolean isIgnoreAdditionalProperties() {
		return ignoreAdditionalProperties;
	}

	public void setIgnoreAdditionalProperties(boolean ignoreAdditionalProperties) {
		this.ignoreAdditionalProperties = ignoreAdditionalProperties;
	}

	public List<Modifier> getModifiers() {
		return modifiers;
	}

	@JsonDeserialize(contentConverter=ModifierConverter.class)
	public void setModifiers(List<Modifier> modifiers) {
		this.modifiers = modifiers;
	}

	@Override
	public String toString() {
		return "Mapping(" + target + " -> " + className + ")";
	}
}