package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.mod.loading.ModClassLoader;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs test code inside the clone of SC:R Crossover: one class loader holds the crossover jar, the SC:R game test fixtures, the SC:R LAN tests (their
 * in-memory LAN scenario) and the crossover's test classes, and loads every class of them from there first, so test code written against the SC:R
 * mod's API (such as {@link CloneGameScenario}) links to the clone's classes of the jar. The packages a mod class loader protects
 * ({@link ModClassLoader#PROTECTED_PACKAGES}) and JUnit come from the test's class loader. Each run is a new class loader, so two runs share no
 * static state of the clone.
 */
final class CloneRunScenario implements AutoCloseable {
    /**
     * The packages that come from the test's class loader besides the protected ones: JUnit and its assertion failures.
     */
    private static final @NotNull List<@NotNull String> SHARED_PACKAGES = List.of("org.junit.", "org.opentest4j.", "org.apiguardian.");
    /**
     * A class of the SC:R LAN test fixtures, whose location the class loader holds.
     */
    private static final @NotNull String LAN_SCENARIO_CLASS = "org.fogofwarlabs.sc4j.scr.net.lan.InMemoryLanScenario";

    /**
     * Loads the classes of its URLs before those of its parent, except the shared packages.
     */
    private static final class CloneClassLoader extends URLClassLoader {
        static {
            registerAsParallelCapable();
        }

        private static boolean isShared(@NotNull String name) {
            return ModClassLoader.isProtected(name) || SHARED_PACKAGES.stream().anyMatch(name::startsWith);
        }


        private CloneClassLoader(@NotNull URL @NotNull [] urls, @NotNull ClassLoader parent) {
            super("scr-crossover-clone", urls, parent);
        }

        @Override
        protected @NotNull Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this.getClassLoadingLock(name)) {
                Class<?> loaded = this.findLoadedClass(name);

                if (loaded == null) {
                    loaded = isShared(name) ? this.getParent().loadClass(name) : this.loadOwnFirst(name);
                }

                if (resolve) {
                    this.resolveClass(loaded);
                }

                return loaded;
            }
        }

        private @NotNull Class<?> loadOwnFirst(@NotNull String name) throws ClassNotFoundException {
            try {
                return this.findClass(name);
            } catch (ClassNotFoundException exception) {
                return this.getParent().loadClass(name);
            }
        }
    }


    private final @NotNull CloneClassLoader loader;

    /**
     * Creates a {@link CloneRunScenario} instance over the built crossover jar.
     *
     * @throws IOException if a class location is not a valid URL
     */
    CloneRunScenario() throws IOException {
        URL fixtures = GameTestResources.class.getProtectionDomain().getCodeSource().getLocation();
        URL tests = CloneRunScenario.class.getProtectionDomain().getCodeSource().getLocation();
        URL lanTests;

        try {
            lanTests = Class.forName(LAN_SCENARIO_CLASS, false, CloneRunScenario.class.getClassLoader()).getProtectionDomain().getCodeSource()
                            .getLocation();
        } catch (ClassNotFoundException exception) {
            throw Exceptions.newIllegalStateException(exception, "The crossover's tests need the SC:R mod's {0}.", LAN_SCENARIO_CLASS);
        }

        this.loader = new CloneClassLoader(new URL[]{ScrCrossoverTestResources.getModJar().toUri().toURL(), fixtures, lanTests, tests},
                                           CloneRunScenario.class.getClassLoader());
    }

    /**
     * Calls a public static method of a test class in the clone, with the given arguments of JDK types. An assertion failure or exception of the
     * method is thrown as it is.
     *
     * @param className  the binary name of the test class
     * @param methodName the method name
     * @param arguments  the arguments; each argument's class is the parameter type
     * @return the method's result
     * @throws Exception if the class or method cannot be found, or the method fails
     */
    @Nullable Object call(@NotNull String className, @NotNull String methodName, @NotNull Object @NotNull ... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];

        for (int index = 0; index < arguments.length; index++) {
            types[index] = arguments[index] instanceof Path ? Path.class : arguments[index].getClass();
        }

        Method method = Class.forName(className, true, this.loader).getMethod(methodName, types);

        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }

            if (exception.getCause() instanceof Error error) {
                throw error;
            }

            throw Exceptions.newIllegalStateException(exception, "{0}.{1} failed.", className, methodName);
        }
    }

    /**
     * Closes the class loader.
     *
     * @throws IOException if the jar cannot be closed
     */
    @Override
    public void close() throws IOException {
        this.loader.close();
    }
}
