/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.base.util;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import joptsimple.internal.Strings;
import net.fabricmc.api.Side;
import net.fabricmc.base.loader.Loader;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * The purpose of this class is to provide an utility for baking mixins from
 * mods into a JAR file at compile time to make accessing APIs provided by them
 * more intuitive in development environment.
 */
public class Prebaker {
	private static class DesprinklingFieldVisitor extends FieldVisitor {
		public DesprinklingFieldVisitor(int api, FieldVisitor fv) {
			super(api, fv);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static class DesprinklingMethodVisitor extends MethodVisitor {
		public DesprinklingMethodVisitor(int api, MethodVisitor mv) {
			super(api, mv);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static class DesprinklingClassVisitor extends ClassVisitor {
		public DesprinklingClassVisitor(int api, ClassVisitor cv) {
			super(api, cv);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
			return new DesprinklingFieldVisitor(Opcodes.ASM5, super.visitField(access, name, desc, signature, value));
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			return new DesprinklingMethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static boolean isSprinkledAnnotation(String desc) {
		//System.out.println(desc);
		return desc.startsWith("Lorg/spongepowered/asm/mixin/transformer/meta");
	}

	// Term proposed by Mumfrey, don't blame me
	public static byte[] desprinkle(byte[] cls) {
		ClassReader reader = new ClassReader(cls);
		ClassWriter writer = new ClassWriter(0);

		reader.accept(new DesprinklingClassVisitor(Opcodes.ASM5, writer), 0);
		return writer.toByteArray();
	}

	public static void main(String[] args) {

		if (args.length < 3) {
			System.out.println("usage: Prebaker [-m mapping-file] <input-jar> <output-jar> <mod-jars...>");
			return;
		}

		int argOffset;
		for (argOffset = 0; argOffset < args.length; argOffset++) {
			if ("-m".equals(args[argOffset])) {

			} else {
				break;
			}
		}

		Set<File> modFiles = new HashSet<>();
		for (int i = argOffset + 2; i < args.length; i++) {
			modFiles.add(new File(args[i]));
		}

		URLClassLoader ucl = (URLClassLoader) Prebaker.class.getClassLoader();
		Launch.classLoader = new LaunchClassLoader(ucl.getURLs());
		Launch.blackboard = new HashMap<>();


		Loader modLoader = new Loader();
		modLoader.load(modFiles);


		try {
			JarInputStream input = new JarInputStream(new FileInputStream(new File(args[argOffset + 0])));
			JarOutputStream output = new JarOutputStream(new FileOutputStream(new File(args[argOffset + 1])));
			JarEntry entry;
			while ((entry = input.getNextJarEntry()) != null) {
				if (entry.getName().endsWith(".class")) {
					byte[] classIn = ByteStreams.toByteArray(input);
					String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
					byte[] classOut = classIn;
					for(IClassTransformer transformer : Launch.classLoader.getTransformers()){
						classOut = transformer.transform(className, className, classOut);
					}

					if (classIn != classOut) {
						System.out.println("Transformed " + className);
						classOut = desprinkle(classOut);
					}
					JarEntry newEntry = new JarEntry(entry.getName());
					newEntry.setComment(entry.getComment());
					newEntry.setSize(classOut.length);
					newEntry.setLastModifiedTime(FileTime.from(Instant.now()));
					output.putNextEntry(newEntry);
					output.write(classOut);
				} else {
					output.putNextEntry(entry);
					ByteStreams.copy(input, output);
				}
			}
			input.close();
			output.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
