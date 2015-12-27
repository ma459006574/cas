package org.jasig.cas.web.flow;

import org.jasig.cas.CasProtocolConstants;
import org.jasig.cas.CentralAuthenticationService;
import org.jasig.cas.authentication.AuthenticationContext;
import org.jasig.cas.authentication.AuthenticationContextBuilder;
import org.jasig.cas.authentication.AuthenticationSystemSupport;
import org.jasig.cas.authentication.AuthenticationException;
import org.jasig.cas.authentication.AuthenticationTransaction;
import org.jasig.cas.authentication.Credential;
import org.jasig.cas.authentication.DefaultAuthenticationContextBuilder;
import org.jasig.cas.authentication.DefaultAuthenticationSystemSupport;
import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.ticket.AbstractTicketException;
import org.jasig.cas.ticket.InvalidTicketException;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.web.support.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import javax.validation.constraints.NotNull;

/**
 * Action to generate a service ticket for a given Ticket Granting Ticket and
 * Service.
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
@Component("generateServiceTicketAction")
public final class GenerateServiceTicketAction extends AbstractAction {

    /** Instance of CentralAuthenticationService. */
    @NotNull
    @Autowired
    @Qualifier("centralAuthenticationService")
    private CentralAuthenticationService centralAuthenticationService;

    @NotNull
    @Autowired(required=false)
    @Qualifier("defaultAuthenticationSystemSupport")
    private AuthenticationSystemSupport authenticationSystemSupport = new DefaultAuthenticationSystemSupport();

    @Override
    protected Event doExecute(final RequestContext context) {
        final Service service = WebUtils.getService(context);
        final String ticketGrantingTicket = WebUtils.getTicketGrantingTicketId(context);

        try {
            final Credential credential = WebUtils.getCredential(context);

            final AuthenticationContextBuilder builder = new DefaultAuthenticationContextBuilder(
                    this.authenticationSystemSupport.getPrincipalElectionStrategy());
            final AuthenticationTransaction transaction = AuthenticationTransaction.wrap(credential);
            this.authenticationSystemSupport.getAuthenticationTransactionManager().handle(transaction,  builder);
            final AuthenticationContext authenticationContext = builder.build(service);

            final ServiceTicket serviceTicketId = this.centralAuthenticationService
                    .grantServiceTicket(ticketGrantingTicket, service, authenticationContext);
            WebUtils.putServiceTicketInRequestScope(context, serviceTicketId);
            return success();

        } catch (final AuthenticationException e) {
            logger.error("Could not verify credentials to grant service ticket", e);
        } catch (final AbstractTicketException e) {
            if (e instanceof InvalidTicketException) {
                this.centralAuthenticationService.destroyTicketGrantingTicket(ticketGrantingTicket);
            }
            if (isGatewayPresent(context)) {
                return result("gateway");
            }
        }

        return error();
    }

    public void setCentralAuthenticationService(final CentralAuthenticationService centralAuthenticationService) {
        this.centralAuthenticationService = centralAuthenticationService;
    }

    public void setAuthenticationSystemSupport(final AuthenticationSystemSupport authenticationSystemSupport) {
        this.authenticationSystemSupport = authenticationSystemSupport;
    }

    /**
     * Checks if {@code gateway} is present in the request params.
     *
     * @param context the context
     * @return true, if gateway present
     */
    protected boolean isGatewayPresent(final RequestContext context) {
        return StringUtils.hasText(context.getExternalContext()
            .getRequestParameterMap().get(CasProtocolConstants.PARAMETER_GATEWAY));
    }
}
