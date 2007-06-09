package com.enea.jcarder.agent.instrument;

import net.jcip.annotations.NotThreadSafe;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.enea.jcarder.agent.StaticEventListener;

import static com.enea.jcarder.agent.instrument.InstrumentationUtilities.getInternalName;

@NotThreadSafe
class DeadLockMethodAdapter extends MethodAdapter {
    private static final String CALLBACK_CLASS_NAME = getInternalName(StaticEventListener.class);
    private final String mClassAndMethodName;
    private final String mClassName;
    private StackAnalyzeMethodVisitor mStack;

    DeadLockMethodAdapter(final MethodVisitor visitor,
                          final String className,
                          final String methodName) {
        super(visitor);
        mClassAndMethodName = className + "." + methodName + "()";
        mClassName = className;
    }

    public void visitInsn(int inst) {
        if (inst == Opcodes.MONITORENTER) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(convertFromJvmInternalNames(mStack.peek()));
            mv.visitLdcInsn(mClassAndMethodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                               CALLBACK_CLASS_NAME,
                               "beforeMonitorEnter",
                   "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V");
        }
        super.visitInsn(inst);
    }

    private String convertFromJvmInternalNames(String s) {
        if (s == null) {
            assert false;
            return "null???";
        } else {
            final String name = s.replace('/', '.');
            if (name.equals(mClassName + ".class")) {
                return "class";
            } else {
                return name;
            }
        }
    }

    void setStackAnalyzer(StackAnalyzeMethodVisitor stack) {
        mStack = stack;
    }
}