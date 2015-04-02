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
package com.collaborne.jsonschema.generator.pojo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class PojoClassGeneratorTest {
	public static class FileSystemJavaFileManager<T extends JavaFileManager> extends ForwardingJavaFileManager<T> {
		private final FileSystem fs;
		private final Charset cs;

		public FileSystemJavaFileManager(T fileManager, FileSystem fs, Charset cs) {
			super(fileManager);
			this.fs = fs;
			this.cs = cs;
		}

		private Kind detectKind(String name) {
			for (Kind kind : Kind.values()) {
				if (kind == Kind.OTHER) {
					continue;
				}
				if (name.endsWith(kind.extension)) {
					return kind;
				}
			}
			return Kind.OTHER;
		}

		@Override
		public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
			if (location == StandardLocation.SOURCE_PATH) {
				Path path = getPathForLocation(location).resolve(packageName.replace('.', '/')).resolve(relativeName);
				if (!Files.isRegularFile(path)) {
					return null;
				}

				Kind kind = detectKind(path.toString());
				FileObject fileObject = new PathFileObject(path, cs);
				if (kind == Kind.OTHER) {
					return fileObject;
				} else {
					return new WrappedJavaFileObject(fileObject, kind);
				}
			} else {
				return super.getFileForInput(location, packageName, relativeName);
			}
		}

		@Override
		public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
			if (location == StandardLocation.SOURCE_OUTPUT || location == StandardLocation.CLASS_OUTPUT) {
				Path path = getPathForLocation(location).resolve(packageName.replace('.', '/')).resolve(relativeName);
				Files.createDirectories(path.getParent());

				Kind kind = detectKind(path.toString());
				FileObject fileObject = new PathFileObject(path, cs);
				if (kind == Kind.OTHER) {
					return fileObject;
				} else {
					return new WrappedJavaFileObject(fileObject, kind);
				}
			} else {
				return super.getFileForInput(location, packageName, relativeName);
			}
		}

		@Override
		public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
			if (location == StandardLocation.SOURCE_PATH) {
				Path path = getPathForLocation(location).resolve(className.replace('.', '/') + kind.extension);
				if (!Files.isRegularFile(path)) {
					return null;
				}

				FileObject fileObject = new PathFileObject(path, cs);
				return new WrappedJavaFileObject(fileObject, kind);
			} else {
				return super.getJavaFileForInput(location, className, kind);
			}
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
			if (location == StandardLocation.SOURCE_OUTPUT || location == StandardLocation.CLASS_OUTPUT) {
				Path path = getPathForLocation(location).resolve(className.replace('.', '/') + kind.extension);
				Files.createDirectories(path.getParent());

				FileObject fileObject = new PathFileObject(path, cs);
				return new WrappedJavaFileObject(fileObject, kind);
			} else {
				return super.getJavaFileForInput(location, className, kind);
			}
		}

		@Override
		public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
			if (location == StandardLocation.SOURCE_PATH || location == StandardLocation.SOURCE_OUTPUT || location == StandardLocation.CLASS_OUTPUT) {
				throw new UnsupportedOperationException();
			} else {
				return super.list(location, packageName, kinds, recurse);
			}
		}

		@Override
		public ClassLoader getClassLoader(Location location) {
			if (location == StandardLocation.CLASS_OUTPUT) {
				return new ClassLoader() {
					@Override
					protected Class<?> findClass(String name) throws ClassNotFoundException {
						try {
							byte[] bytes = Files.readAllBytes(getPathForLocation(location).resolve(name.replace('.', '/') + Kind.CLASS.extension));
							return defineClass(name, bytes, 0, bytes.length);
						} catch (IOException e) {
							throw new ClassNotFoundException(name, e);
						}
					}
				};
			} else {
				return super.getClassLoader(location);
			}
		}

		public Path getPathForLocation(Location location) {
			return fs.getPath("/", location.getName());
		}
	}

	protected static class PathFileObject implements FileObject {
		private final Path path;
		private final Charset cs;

		public PathFileObject(Path path, Charset cs) {
			this.path = path;
			this.cs = cs;
		}

		public Path getPath() {
			return path;
		}

		public Charset getCharset() {
			return cs;
		}

		@Override
		public URI toUri() {
			return path.toUri();
		}

		@Override
		public String getName() {
			return toUri().getPath();
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return Files.newInputStream(path);
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			return Files.newOutputStream(path);
		}

		@Override
		public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
			return Files.newBufferedReader(path, cs);
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			return new String(Files.readAllBytes(path), cs);
		}

		@Override
		public Writer openWriter() throws IOException {
			return Files.newBufferedWriter(path, cs);
		}

		@Override
		public long getLastModified() {
			try {
				return Files.getLastModifiedTime(path).toMillis();
			} catch (IOException e) {
				return 0L;
			}
		}

		@Override
		public boolean delete() {
			try {
				return Files.deleteIfExists(path);
			} catch (IOException e) {
				return false;
			}
		}
	}

	// This is a mix between ForwardingJavaFileObject and SimpleJavaFileObject: it forwards the FileObject methods,
	// and uses the simple implementation for the methods of JavaFileObject
	protected static class WrappedJavaFileObject extends SimpleJavaFileObject {
		private final FileObject fileObject;

		public WrappedJavaFileObject(FileObject fileObject, Kind kind) {
			super(fileObject.toUri(), kind);
			this.fileObject = fileObject;
		}

		@Override
		public URI toUri() {
			return fileObject.toUri();
		}

		@Override
		public String getName() {
			return fileObject.getName();
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return fileObject.openInputStream();
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			return fileObject.openOutputStream();
		}

		@Override
		public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
			return fileObject.openReader(ignoreEncodingErrors);
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			return fileObject.getCharContent(ignoreEncodingErrors);
		}

		@Override
		public Writer openWriter() throws IOException {
			return fileObject.openWriter();
		}

		@Override
		public long getLastModified() {
			return fileObject.getLastModified();
		}

		@Override
		public boolean delete() {
			return fileObject.delete();
		}
	}

	private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
	private final ObjectMapper objectMapper = JacksonUtils.newMapper();

	private FileSystem fs;

	@Before
	public void setUp() {
		fs = Jimfs.newFileSystem(Configuration.unix());
	}

	@After
	public void tearDown() throws IOException {
		fs.close();
	}

	@Test
	public void generateSchemaWithDefaultString() throws IOException, CodeGenerationException, ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		final Charset cs = StandardCharsets.UTF_8;
		FileSystemJavaFileManager<JavaFileManager> fileManager = new FileSystemJavaFileManager<>(compiler.getStandardFileManager(null, Locale.ENGLISH, cs), fs, cs);

		URI typeUri = URI.create("#");
		Map<URI, SchemaTree> schemas = new HashMap<>();
		schemas.put(typeUri, createSchemaTree("{\"type\":\"object\", \"properties\": { \"key\": { \"type\": \"string\", \"default\":\"foo\"}}}"));

		ClassName generatedClassName;
		Set<ClassName> allGeneratedClassNames = new HashSet<>();
		PojoClassGenerator classGenerator = new PojoClassGenerator();
		PojoGenerator pojoGenerator = new PojoGenerator(classGenerator, new PojoArrayGenerator(), new PojoStringGenerator()) {
			@Override
			protected SchemaTree getSchema(SchemaLoader schemaLoader, URI uri) throws ProcessingException {
				SchemaTree schema = schemas.get(uri.resolve("#"));
				if (!uri.getFragment().isEmpty()) {
					try {
						schema = schema.append(new JsonPointer(uri.getFragment()));
					} catch (JsonPointerException e) {
						// XXX: unlikely, but alas.
						throw new ProcessingException();
					}
				}
				return schema;
			}

			@Override
			protected Path getClassSourceFile(ClassName className) {
				// XXX: should have a proper API for that
				allGeneratedClassNames.add(className);
				return super.getClassSourceFile(className);
			}
		};
		pojoGenerator.setOutputDirectory(fileManager.getPathForLocation(StandardLocation.SOURCE_PATH));
		pojoGenerator.addMapping(typeUri, new Mapping(typeUri, new ClassName("test", "Test")));

		generatedClassName = pojoGenerator.generate(typeUri);

		// Compile it
		List<JavaFileObject> compilationUnits = new ArrayList<>(allGeneratedClassNames.size());
		for (ClassName className : allGeneratedClassNames) {
			String fqcn = className.getPackageName() + "." + className.getRawClassName();
			JavaFileObject compilationUnit = fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, fqcn, Kind.SOURCE);
			compilationUnits.add(compilationUnit);
		}
		CompilationTask task = compiler.getTask(null, fileManager, null, null, null, compilationUnits);
		assertTrue(task.call().booleanValue());

		// Now the magic: load it at runtime, and check the value
		ClassLoader cl = fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);

		String fqcn = generatedClassName.getPackageName() + "." + generatedClassName.getRawClassName();
		Class<?> generatedClass = Class.forName(fqcn, true, cl);
		Method getKeyMethod = generatedClass.getMethod("getKey");
		Object instance = generatedClass.newInstance();
		assertEquals("foo", getKeyMethod.invoke(instance));
	}

	private SchemaTree createSchemaTree(String json) throws IOException {
		JsonNode schemaNode = new JsonNodeReader(objectMapper).fromReader(new StringReader(json));
		return new SchemaLoader().load(schemaNode);
	}
}
