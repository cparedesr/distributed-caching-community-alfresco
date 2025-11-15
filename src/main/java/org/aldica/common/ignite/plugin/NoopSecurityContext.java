package org.aldica.common.ignite.plugin;

import java.io.Serializable;

import org.apache.ignite.internal.processors.security.SecurityContext;
import org.apache.ignite.plugin.security.SecurityPermission;
import org.apache.ignite.plugin.security.SecuritySubject;

/**
 * Implementación "no-op" de SecurityContext para cuando Ignite
 * se ejecuta sin seguridad. Siempre permite todas las operaciones.
 *
 * En Ignite 2.17.x la interfaz interna SecurityContext ya no declara
 * los métodos *OperationAllowed(...). Los mantenemos sin @Override
 * por compatibilidad con el código del proyecto.
 */
public class NoopSecurityContext implements SecurityContext, Serializable {

    private static final long serialVersionUID = 1794411284528886140L;

    protected final SecuritySubject subject;

    public NoopSecurityContext(final SecuritySubject subject) {
        this.subject = subject;
    }

    @Override
    public SecuritySubject subject() {
        return this.subject;
    }

    // ----- Métodos "legacy" mantenidos sin @Override -----

    public boolean taskOperationAllowed(final String taskClsName,
                                        final SecurityPermission perm) {
        return true;
    }

    public boolean cacheOperationAllowed(final String cacheName,
                                         final SecurityPermission perm) {
        return true;
    }

    public boolean systemOperationAllowed(final SecurityPermission perm) {
        return true;
    }

    public boolean serviceOperationAllowed(final String srvcName,
                                           final SecurityPermission perm) {
        return true;
    }
}