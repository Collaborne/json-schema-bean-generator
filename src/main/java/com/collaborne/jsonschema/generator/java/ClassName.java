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
package com.collaborne.jsonschema.generator.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;


public class ClassName {
	public static final ClassName VOID = ClassName.create(Void.TYPE);
	private final String packageName;
	private final String rawClassName;
	private final ClassName[] typeArguments;
	
	public ClassName(@Nonnull String packageName, @Nonnull String rawClassName, @Nullable ClassName... typeArguments) {
		this.packageName = packageName;
		this.rawClassName = rawClassName;
		this.typeArguments = typeArguments;
	}
	
	public String getPackageName() {
		return packageName;
	}
	
	public String getRawClassName() {
		return rawClassName;
	}
	
	public ClassName[] getTypeArguments() {
		return typeArguments;
	}
	
	public static ClassName create(Class<?> actualClass, ClassName... typeArguments) {
		Package actualPackage = actualClass.getPackage();
		String packageName = actualPackage == null ? "" : actualPackage.getName();
		String className = actualClass.getName();
		String rawClassName;
		if (packageName.isEmpty()) {
			rawClassName = className;
		} else {
			rawClassName = className.substring(packageName.length() + 1);
		}
		return new ClassName(packageName, rawClassName, typeArguments);
	}
	
	public static ClassName parse(String fqcn) {
		// FIXME: This has some issues with inner classes and generics
		//        One way out could be to use binary names instead.
		int lastDotIndex = fqcn.lastIndexOf('.');
		String rawClassName = fqcn.substring(lastDotIndex + 1, fqcn.length());
		
		String packageName;
		if (lastDotIndex == -1) {
			packageName = "";
		} else {
			packageName = fqcn.substring(0, lastDotIndex);
		}
		
		return new ClassName(packageName, rawClassName);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return appendTo(sb).toString();
	}
	
	@VisibleForTesting
	protected StringBuilder appendTo(StringBuilder appendable) {
		if (!packageName.isEmpty()) {
			appendable.append(packageName);
			appendable.append(".");
		}
		appendable.append(rawClassName);
		
		// Add the type arguments
		if (typeArguments != null && typeArguments.length > 0) {
			appendable.append("<");
			for (ClassName typeArgument : typeArguments) {
				typeArgument.appendTo(appendable);
			}
			appendable.append(">");
		}
		return appendable;
	}
}
