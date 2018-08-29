package com.github.axet.audiolibrary.encoders;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Natives {

    public static String ARCH = Build.CPU_ABI;

    public static class ArchFirst implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            String p1 = o1.getPath();
            String p2 = o2.getPath();
            boolean b1 = p1.contains(ARCH);
            boolean b2 = p2.contains(ARCH);
            if (b1 && b2)
                return p1.compareTo(p2);
            if (b1)
                return -1;
            if (b2)
                return 1;
            return p1.compareTo(p2);
        }
    }

    public static void loadLibraries(Context context, String... libs) {
        try {
            for (String l : libs) {
                System.loadLibrary(l); // API16 failed to find dependencies
            }
        } catch (ExceptionInInitializerError | UnsatisfiedLinkError e) { // API15 crash
            for (String l : libs) {
                Natives.loadLibrary(context, l);
            }
        }
    }

    /**
     * API15 crash while loading wrong arch native libraries. We need to find and load them manually.
     * <p>
     * Caused by: java.lang.UnsatisfiedLinkError: Cannot load library: reloc_library[1286]:  1823 cannot locate '__aeabi_idiv0'...
     * at java.lang.Runtime.loadLibrary(Runtime.java:370)
     * at java.lang.System.loadLibrary(System.java:535)
     */
    public static void loadLibrary(final Context context, String libname) {
        String file = search(context, System.mapLibraryName(libname));
        if (file == null)
            throw new UnsatisfiedLinkError("file not found: " + libname);
        System.load(file);
    }

    public static String search(Context context, String filename) {
        String dir = context.getApplicationInfo().nativeLibraryDir;
        if (dir.endsWith(ARCH)) {
            File f = new File(dir);
            f = f.getParentFile();
            String lib = search(f, filename);
            if (lib != null)
                return lib;
        }
        return search(new File(dir), filename);
    }

    public static String search(File f, String filename) {
        List<File> ff = list(f, filename);
        Collections.sort(ff, new ArchFirst());
        if (ff.size() == 0)
            return null;
        return ff.get(0).getAbsolutePath();
    }

    public static ArrayList<File> list(File f, String filename) {
        ArrayList<File> ff = new ArrayList<>();
        File[] aa = f.listFiles();
        if (aa != null) {
            for (File a : aa) {
                if (a.isDirectory()) {
                    ArrayList<File> mm = list(a, filename);
                    ff.addAll(mm);
                }
                if (a.getName().equals(filename))
                    ff.add(a);
            }
        }
        return ff;
    }
}
