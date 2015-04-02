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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;


public final class ClassName {
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
	
	public static ClassName create(Class<?> actualClass) {
		return create(actualClass, (ClassName[]) null);
	}

	public static ClassName create(Class<?> actualClass, ClassName... typeArguments) {
		Package actualPackage = actualClass.getPackage();
		String packageName = actualPackage == null ? "" : actualPackage.getName();
		String className = actualClass.getCanonicalName();
		String rawClassName;
		if (packageName.isEmpty()) {
			rawClassName = className;
		} else {
			rawClassName = className.substring(packageName.length() + 1);
		}
		return new ClassName(packageName, rawClassName, typeArguments);
	}
	
	public static ClassName parse(String value) {
		String fqcn = value.trim();

		ClassName[] typeArguments;
		int bracketIndex = fqcn.indexOf('<');
		if (bracketIndex != -1) {
			// Type with generic type arguments, filter those out and process them recursively
			List<ClassName> arguments = new ArrayList<>();
			assert fqcn.charAt(fqcn.length() - 1) == '>';
			int lastStart = bracketIndex + 1;
			int level = 0;
			for (int i = lastStart; i < fqcn.length(); i++) {
				if ((fqcn.charAt(i) == ',' || fqcn.charAt(i) == '>') && level == 0) {
					arguments.add(ClassName.parse(fqcn.substring(lastStart, i)));
					lastStart = i + 1;
				} else if (fqcn.charAt(i) == '>') {
					level--;
				} else if (fqcn.charAt(i) == '<') {
					level++;
				}
			}
			typeArguments = arguments.toArray(new ClassName[arguments.size()]);
		} else {
			typeArguments = null;
		}

		int end = bracketIndex == -1 ? fqcn.length() : bracketIndex;
		// FIXME: This has some issues with inner classes probably
		//        One way out could be to use binary names instead.
		int lastDotIndex = fqcn.lastIndexOf('.', end);
		String rawClassName = fqcn.substring(lastDotIndex + 1, end);

		String packageName;
		if (lastDotIndex == -1) {
			packageName = "";
		} else {
			packageName = fqcn.substring(0, lastDotIndex);
		}

		return new ClassName(packageName, rawClassName, typeArguments);
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
			for (int i = 0; i < typeArguments.length; i++) {
				if (i > 0) {
					appendable.append(",");
				}
				typeArguments[i].appendTo(appendable);
			}
			appendable.append(">");
		}
		return appendable;
	}

	@Override
	public int hashCode() {
		return Objects.hash((Object[]) typeArguments) ^ Objects.hash(rawClassName, packageName);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ClassName)) {
			return false;
		}

		ClassName other = (ClassName) obj;
		return Objects.equals(packageName, other.getPackageName()) && Objects.equals(rawClassName, other.getRawClassName()) && Arrays.equals(typeArguments, other.getTypeArguments());
	}
}
