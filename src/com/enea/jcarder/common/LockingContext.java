package com.enea.jcarder.common;

import net.jcip.annotations.ThreadSafe;

/**
 * An instance of this class represent the context for the acquiring of a lock.
 */
@ThreadSafe
public final class LockingContext {
    /**
     * The name of the thread that acquired a lock.
     */
    private final String mThreadName;

    /**
     * A textual description of how the lock object was addressed. For example:
     * "this", "com.enea.jcarder.Foo.mBar" or "com.enea.jcarder.Foo.getLock()"
     */
    private final String mLockReference;

    /**
     * The method that acquired a lock, on the
     * format: "com.enea.jcarder.Foo.bar()".
     */
    private final String mMethodWithClass;
    // TODO include row number in MethodWithClass?

    public LockingContext(String threadName,
                          String lockReference,
                          String methodWithClass) {
        mThreadName = threadName;
        mLockReference = lockReference;
        mMethodWithClass = methodWithClass;
    }

    public LockingContext(Thread thread,
                          String lockReference,
                          String methodWithClass) {
        this(thread.getName(), lockReference, methodWithClass);
    }

    public String getLockReference() {
        return mLockReference;
    }

    public String getMethodWithClass() {
        return mMethodWithClass;
    }

    public String getThreadName() {
        return mThreadName;
    }

    public boolean alike(LockingContext other) {
        return mLockReference.equals(other.mLockReference)
               && mMethodWithClass.equals(other.mMethodWithClass);
    }

    public boolean equals(Object other) {
        try {
            if (other == null) {
                return false;
            }
            // TODO Maybe use interned strings to improve performance?
            final LockingContext otherContext = (LockingContext) other;
            return mThreadName.equals(otherContext.mThreadName)
                   && mLockReference.equals(otherContext.mLockReference)
                   && mMethodWithClass.equals(otherContext.mMethodWithClass);
        } catch (ClassCastException e) {
            return false;
        }
    }

    public int hashCode() {
        return mThreadName.hashCode()
               + mMethodWithClass.hashCode()
               + mLockReference.hashCode();
    }

    public String toString() {
        return "Thread: " + mThreadName
               + " LockRef: " + mLockReference
               + " Method:  " + mMethodWithClass;
    }
}
