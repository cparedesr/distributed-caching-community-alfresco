package org.aldica.common.ignite.plugin;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.apache.ignite.plugin.security.SecurityPermissionSet;
import org.apache.ignite.plugin.security.SecuritySubject;
import org.apache.ignite.plugin.security.SecuritySubjectType;

/**
 * Implementación simple de SecuritySubject.
 *
 * En Ignite 2.17.x la interfaz ya no expone permissions().
 * Lo mantenemos como método propio para que el resto del código
 * pueda seguir consultando el conjunto de permisos si lo necesita.
 */
public class SimpleSecuritySubject implements SecuritySubject {

    private static final long serialVersionUID = 8577031703972371596L;

    protected final UUID id;

    protected final SecuritySubjectType type;

    protected final Object login;

    protected final InetSocketAddress address;

    protected final SecurityPermissionSet permissions;

    public SimpleSecuritySubject(final UUID id,
                                 final SecuritySubjectType type,
                                 final Object login,
                                 final InetSocketAddress address,
                                 final SecurityPermissionSet permissions) {
        this.id = id;
        this.type = type;
        this.login = login;
        this.address = address;
        this.permissions = permissions;
    }

    @Override
    public UUID id() {
        return this.id;
    }

    @Override
    public SecuritySubjectType type() {
        return this.type;
    }

    @Override
    public Object login() {
        return this.login;
    }

    @Override
    public InetSocketAddress address() {
        return this.address;
    }

    /**
     * Método "propio" del proyecto: devuelve el conjunto de permisos
     * asociado a este sujeto.
     */
    public SecurityPermissionSet permissions() {
        return this.permissions;
    }
}