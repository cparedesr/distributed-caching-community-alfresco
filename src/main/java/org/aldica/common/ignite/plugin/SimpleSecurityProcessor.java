/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.plugin;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.internal.processors.security.GridSecurityProcessor;
import org.apache.ignite.internal.processors.security.SecurityContext;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.plugin.PluginContext;
import org.apache.ignite.plugin.security.AuthenticationContext;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.apache.ignite.plugin.security.SecurityException;
import org.apache.ignite.plugin.security.SecurityPermission;
import org.apache.ignite.plugin.security.SecuritySubject;
import org.apache.ignite.plugin.security.SecuritySubjectType;
import org.apache.ignite.spi.IgniteNodeValidationResult;
import org.apache.ignite.spi.discovery.DiscoveryDataBag;
import org.apache.ignite.spi.discovery.DiscoveryDataBag.GridDiscoveryData;
import org.apache.ignite.spi.discovery.DiscoveryDataBag.JoiningNodeDiscoveryData;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Procesador de seguridad "simple".
 *
 * En esta versión para Ignite 2.17.x, la seguridad se considera
 * desactivada:
 *
 *  - {@link #enabled()} devuelve siempre {@code false}
 *  - authenticate / authenticateNode aceptan siempre y devuelven
 *    un {@link NoopSecurityContext}
 *  - {@link #securityContext(UUID)} está implementado para evitar
 *    la {@link UnsupportedOperationException} del método por defecto
 *    introducido en IEP-41.
 *
 * Es decir: Ignite se comportará como si la seguridad estuviera
 * deshabilitada, aunque el plugin siga registrado.
 *
 * @author Axel Faust
 */
public class SimpleSecurityProcessor implements GridSecurityProcessor
{

    public static final String ATTR_SECURITY_TIER = IgniteNodeAttributes.ATTR_PREFIX + ".security.tier";

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSecurityProcessor.class);

    protected final PluginContext ctx;

    protected final SimpleSecurityPluginConfiguration configuration;

    protected final Map<UUID, SecuritySubject> authenticatedSubjects = new HashMap<>();

    /**
     * Inicializa el procesador de seguridad en modo "no-auth".
     *
     * @param ctx
     *     plugin context
     * @param configuration
     *     configuración opcional del plugin (actualmente ignorada para
     *     la lógica de autenticación, ya que está desactivada)
     */
    public SimpleSecurityProcessor(final PluginContext ctx, final SimpleSecurityPluginConfiguration configuration)
    {
        this.ctx = ctx;
        this.configuration = configuration;

        LOGGER.info(
                "SimpleSecurityProcessor inicializado en modo SIN SEGURIDAD (no-auth): "
                        + "no se validarán credenciales ni se aplicarán permisos; "
                        + "todas las autenticaciones serán aceptadas.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityContext authenticateNode(final ClusterNode node, final SecurityCredentials cred) throws IgniteCheckedException
    {
        final AuthenticationContext ctxt = new AuthenticationContext();
        ctxt.credentials(cred);
        ctxt.nodeAttributes(node.attributes());
        ctxt.subjectType(node.isClient() ? SecuritySubjectType.REMOTE_CLIENT : SecuritySubjectType.REMOTE_NODE);
        ctxt.subjectId(node.id());
        ctxt.address(new InetSocketAddress(node.addresses().iterator().next(),
                node instanceof TcpDiscoveryNode ? ((TcpDiscoveryNode) node).discoveryPort() : 0));

        // Con seguridad desactivada, simplemente devolvemos un contexto "no-op".
        return this.validateCredentials(ctxt, node.isClient());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isGlobalNodeAuthentication()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityContext authenticate(final AuthenticationContext ctx) throws IgniteCheckedException
    {
        SecuritySubjectType type = ctx.subjectType();
        if (type == null)
        {
            // Tipo por defecto si no viene informado
            type = SecuritySubjectType.REMOTE_NODE;
            ctx.subjectType(type);
        }

        final boolean asClient = type == SecuritySubjectType.REMOTE_CLIENT;
        return this.validateCredentials(ctx, asClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<SecuritySubject> authenticatedSubjects() throws IgniteCheckedException
    {
        return new ArrayList<>(this.authenticatedSubjects.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecuritySubject authenticatedSubject(final UUID subjId) throws IgniteCheckedException
    {
        return this.authenticatedSubjects.get(subjId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws IgniteCheckedException
    {
        final IgniteConfiguration igniteConfiguration = this.ctx.igniteConfiguration();

        // decoupling de userAttributes
        @SuppressWarnings("unchecked")
        Map<String, Object> userAttributes = (Map<String, Object>) igniteConfiguration.getUserAttributes();
        userAttributes = userAttributes != null ? new HashMap<>(userAttributes) : new HashMap<>();
        igniteConfiguration.setUserAttributes(userAttributes);

        // Aunque la seguridad está desactivada, mantenemos la publicación
        // de credenciales / tier en atributos de nodo por compatibilidad.
        final SecurityCredentials credentials = this.configuration != null ? this.configuration.getCredentials() : null;
        if (credentials != null)
        {
            userAttributes.put(IgniteNodeAttributes.ATTR_SECURITY_CREDENTIALS, credentials);
        }

        final String tierAttributeValue = this.configuration != null ? this.configuration.getTierAttributeValue() : null;
        if (tierAttributeValue != null)
        {
            userAttributes.put(ATTR_SECURITY_TIER, tierAttributeValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(final boolean cancel) throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onKernalStart(final boolean active) throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onKernalStop(final boolean cancel)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectJoiningNodeData(final DiscoveryDataBag dataBag)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectGridNodeData(final DiscoveryDataBag dataBag)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGridDataReceived(final GridDiscoveryData data)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onJoiningNodeDataReceived(final JoiningNodeDiscoveryData data)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printMemoryStats()
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IgniteNodeValidationResult validateNode(final ClusterNode node)
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IgniteNodeValidationResult validateNode(final ClusterNode node, final JoiningNodeDiscoveryData discoData)
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DiscoveryDataExchangeType discoveryDataType()
    {
        return DiscoveryDataExchangeType.PLUGIN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisconnected(final IgniteFuture<?> reconnectFut) throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IgniteInternalFuture<?> onReconnected(final boolean clusterRestarted) throws IgniteCheckedException
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void authorize(final String name, final SecurityPermission perm, final SecurityContext securityCtx) throws SecurityException
    {
        // NO-OP - sin ACLs, todo permitido
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSessionExpired(final UUID subjId)
    {
        this.authenticatedSubjects.remove(subjId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enabled()
    {
        // MUY IMPORTANTE:
        // Devuelve false para que Ignite trate la seguridad como desactivada.
        return false;
    }

    /**
     * Nuevo método por IEP-41. La implementación por defecto lanza
     * UnsupportedOperationException, así que debemos sobrescribirla.
     *
     * {@inheritDoc}
     */
    @Override
    public SecurityContext securityContext(final UUID subjId)
    {
        final SecuritySubject subj = this.authenticatedSubjects.get(subjId);
        return subj != null ? new NoopSecurityContext(subj) : null;
    }

    /**
     * En modo "no-auth" esta validación simplemente construye un sujeto y un
     * contexto de seguridad "no-op" y los devuelve, sin comprobar credenciales.
     *
     * @param ctx
     *     contexto de autenticación
     * @param asClient
     *     se pasa true si el sujeto es un cliente
     * @return contexto de seguridad que permite todas las operaciones
     */
    protected SecurityContext validateCredentials(final AuthenticationContext ctx, final boolean asClient)
    {
        final Object login = ctx.credentials() != null ? ctx.credentials().getLogin() : null;

        final SecuritySubject securitySubject = new SimpleSecuritySubject(
                ctx.subjectId(),
                ctx.subjectType(),
                login,
                ctx.address(),
                new NoopSecurityPermissionSet());

        this.authenticatedSubjects.put(ctx.subjectId(), securitySubject);

        LOGGER.debug("Security disabled: aceptando {} ({}) sin validar credenciales (tipo={})",
                ctx.subjectId(), ctx.address(), ctx.subjectType());

        return new NoopSecurityContext(securitySubject);
    }

}
