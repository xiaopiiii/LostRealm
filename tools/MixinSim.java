import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 离线验证 mixin 注入后的字节码能否通过 JVM 校验器。
 *
 * <h2>为什么需要它</h2>
 * Mixin 注入产生的字节码要等类加载时才校验，注入错了不会在编译期报错，
 * 而是游戏启动后抛 {@code VerifyError} —— beta54 就是这样把 JEI 整个搞没的
 * （注入点在 TAIL，处理函数访问 this，而 0 号槽已被复用成 Iterator）。
 * 这个工具把注入行为复刻一遍，直接跑 ASM 的验证器，构建前就能发现。
 *
 * <h2>用法</h2>
 * <pre>
 * asm = asm-9.8.jar;asm-tree-9.8.jar;asm-analysis-9.8.jar;asm-util-9.8.jar（Gradle 缓存里）
 * javac -encoding UTF-8 -cp "$asm" -d . tools/MixinSim.java
 * java -cp ".;$asm" MixinSim &lt;jei jar&gt; &lt;joined-srg jar&gt; &lt;forge-srg jar&gt; &lt;本模组 jar&gt;
 * </pre>
 * 输出应当只有「HEAD 注入 => 通过校验」；对照组本来就会失败，用来证明校验器有效。
 */
public class MixinSim {

    static final String TARGET = "mezz/jei/gui/overlay/ingredients/IngredientListRenderer";
    static final String METHOD = "render";
    static final String DESC = "(Lnet/minecraft/client/gui/GuiGraphics;)V";
    static final String OWNER = "com/lost_realm/qol/jei/JeiVariantFold";
    static final String CALL = "captureAndDraw";
    static final String CALL_DESC =
            "(Lnet/minecraft/client/gui/GuiGraphics;Lmezz/jei/gui/overlay/ingredients/IngredientListRenderer;)V";

    public static void main(String[] args) throws Exception {
        String jei = args[0], mc = args[1], forge = args[2], our = args[3];
        byte[] orig = readEntry(jei, TARGET + ".class");
        System.out.println("原始类大小 " + orig.length);

        URLClassLoader loader = new URLClassLoader(new URL[]{
                Paths.get(our).toUri().toURL(),
                Paths.get(jei).toUri().toURL(),
                Paths.get(mc).toUri().toURL(),
                Paths.get(forge).toUri().toURL()
        }, MixinSim.class.getClassLoader());

        check("【新】HEAD 注入（当前实现）", transform(orig, loader, true), loader);
        check("【旧】TAIL 注入（beta54，对照组）", transform(orig, loader, false), loader);
    }

    static byte[] transform(byte[] orig, ClassLoader loader, boolean head) {
        ClassReader cr = new ClassReader(orig);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                try {
                    Class<?> ca = Class.forName(a.replace('/', '.'), false, loader);
                    Class<?> cb = Class.forName(b.replace('/', '.'), false, loader);
                    if (ca.isAssignableFrom(cb)) return a;
                    if (cb.isAssignableFrom(ca)) return b;
                    if (ca.isInterface() || cb.isInterface()) return "java/lang/Object";
                    do { ca = ca.getSuperclass(); } while (!ca.isAssignableFrom(cb));
                    return ca.getName().replace('.', '/');
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if (!METHOD.equals(name) || !DESC.equals(desc)) return mv;
                if (head) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 1);   // guiGraphics
                            mv.visitVarInsn(Opcodes.ALOAD, 0);   // this
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, CALL, CALL_DESC, false);
                        }
                    };
                }
                // 对照组：在最后一条 RETURN 之前注入，并在处理函数里访问 this
                // （此时 0 号槽已经被复用成 Iterator —— 正是 beta54 崩溃的原因）
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            mv.visitVarInsn(Opcodes.ALOAD, 1);
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, CALL, CALL_DESC, false);
                        }
                        mv.visitInsn(opcode);
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    static void check(String title, byte[] bytes, ClassLoader loader) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            CheckClassAdapter.verify(new ClassReader(bytes), loader, false, pw);
            String err = sw.toString();
            if (err.isEmpty()) {
                System.out.println(title + " => 通过校验");
            } else {
                System.out.println(title + " => 校验失败:");
                System.out.println(err.substring(0, Math.min(1200, err.length())));
            }
        } catch (Throwable t) {
            System.out.println(title + " => 抛异常: " + t);
            String err = sw.toString();
            if (!err.isEmpty()) System.out.println(err.substring(0, Math.min(1200, err.length())));
        }
        System.out.println();
    }

    static byte[] readEntry(String jar, String entry) throws Exception {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
            return zf.getInputStream(zf.getEntry(entry)).readAllBytes();
        }
    }
}
