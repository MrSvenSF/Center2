package net.managerhub.center.common.module;

import java.util.Set;

/**
 * Remembers which installed modules an administrator switched off.
 *
 * <p>Only the core decides this. A module never stores its own activation state
 * and the module jar is never changed.</p>
 *
 * <p>Both methods report a failure instead of hiding it. An unreadable state must
 * never look like "nothing is switched off", and a failed write must never look
 * like a successfully remembered decision.</p>
 */
public interface ModuleStateStore {

    /**
     * @return the ids of every module an administrator switched off, in lower case
     * @throws ModuleStateException if the stored state cannot be read
     */
    Set<String> disabledModules() throws ModuleStateException;

    /**
     * @param moduleId id of the module
     * @param disabled {@code true} if the module was switched off by an administrator
     * @throws ModuleStateException if the decision cannot be stored
     */
    void setModuleDisabled(String moduleId, boolean disabled) throws ModuleStateException;
}
