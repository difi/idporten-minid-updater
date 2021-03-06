package no.minid.updater.service.impl;

import no.difi.kontaktinfo.dto.UserUpdateMessage;
import no.idporten.domain.user.EmailAddress;
import no.idporten.domain.user.MinidUser;
import no.idporten.domain.user.MobilePhoneNumber;
import no.idporten.domain.user.PersonNumber;
import no.minid.exception.MinidUserNotFoundException;
import no.minid.service.MinIDService;
import no.minid.updater.exception.LdapSystemException;
import no.minid.updater.service.UpdaterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service used for updating MinID user with updated contact info.
 * @author Kons-fbo
 *
 */
@Service
public class UpdaterServiceImpl implements UpdaterService {
    private static final Logger LOG = LoggerFactory.getLogger(UpdaterServiceImpl.class.getName());

    private MinIDService minIDService;

    public UpdaterServiceImpl(MinIDService minIDService) {
        this.minIDService = minIDService;
    }

    /**
     * Updates the MinID user with the given user data.
     * User is only updated if input data lastModified is newer than profile update date on MinID user.
     * @param updatedUserData Modified user data.
     */
    @Override
    public void processUpdateMessage(UserUpdateMessage updatedUserData) {
        LOG.debug("processUpdateMessage(updatedUserData:" + updatedUserData + ")");
        MinidUser contact = readContactInfo(updatedUserData.getSsn());
        if(contact == null) {
            LOG.error("Minid user with ssn:'" + updatedUserData.getSsn() + "' not found in MinID.");
            return;
        } else if(contact.isDummy()) {
            LOG.warn("Minid user with ssn:'" + updatedUserData.getSsn() + "' was a dummy user. Not updated!");
            return;
        }
        updateContactInfo(updatedUserData, contact);
    }


    /**
     * Fetches the user identity object from MinID DAO.
     * @param uid User UID. Same as SSN
     * @return The identity for given UID. If not found, null is returned.
     * * @throws LdapSystemException Thrown if ldap read fails.
     */
    private MinidUser readContactInfo(String uid) {
        try {
            return minIDService.findByPersonNumber(new PersonNumber(uid));
        } catch (RuntimeException e) {
            throw new LdapSystemException("Couldn't READ user identity from MinID for UID:'" + uid, e);
        }
    }

    /**
     * Updates the MinId user store with the updated user data.
     * User is only updated if last updated timestamp on updated user data is newer than profile updated timestamp on existing user.
     * @param updatedUserData The user will be updated with this data.
     * @param existingContact The existing user data from MinID.
     * @throws LdapSystemException Thrown if ldap update fails.
     */
    void updateContactInfo(UserUpdateMessage updatedUserData, MinidUser existingContact) {
        // We only update user data in MinID if received updated data is modified after existing MinID profile data.
        if(existingContact.getProfileUpdatedDate() == null ||
                updatedUserData.getLastModified().after(existingContact.getProfileUpdatedDate())) {
            existingContact.setEmail(new EmailAddress(updatedUserData.getEmail()));
            existingContact.setPhoneNumber( new MobilePhoneNumber(updatedUserData.getMobile()) );
            existingContact.setProfileUpdatedDate(updatedUserData.getLastModified());

            try {
                minIDService.updateContactInformation(existingContact);
                LOG.info("MinID user with uid:'" + existingContact.getUid() + "' updated with new contact information");
            } catch (RuntimeException e) {
                throw new LdapSystemException("Couldn't UPDATE user from MinID for UID:'" + updatedUserData.getSsn()
                        + "'. Contact info not updated. Data: " + updatedUserData, e);
            } catch (MinidUserNotFoundException e) {
                LOG.error("Minid user with ssn:'" + updatedUserData.getSsn() + "' not found in MinID.", e);
            }
        } else {
            LOG.warn("MinID user with ssn:'" + existingContact.getUid() + "' NOT updated since profile date was newer than received data.");
        }
    }
}
