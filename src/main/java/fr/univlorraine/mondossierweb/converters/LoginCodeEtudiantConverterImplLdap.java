/**
 * ESUP-Portail MONDOSSIERWEB - Copyright (c) 2007 ESUP-Portail consortium
 */
package fr.univlorraine.mondossierweb.converters;




import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.stereotype.Component;




/**
 * Classe qui sait récupérer le login depuis le code étudiant.
 * @author Charlie Dubois
 *
 */
@Component(value="loginFromCodetuLdapImpl")
public class LoginCodeEtudiantConverterImplLdap implements LoginCodeEtudiantConverterInterface {

	private Logger LOG = LoggerFactory.getLogger(LoginCodeEtudiantConverterImplLdap.class);
	
	
	@Resource
	private transient LdapUserSearch ldapEtudiantSearch;
	
	


	public LoginCodeEtudiantConverterImplLdap() {
		super();
	}


	public String getLoginFromCodEtu(final String codetu) {
		
		
		try {
			if(ldapEtudiantSearch.searchForUser(codetu)!=null){
				String[] vals= ldapEtudiantSearch.searchForUser(codetu).getStringAttributes("uid");
				if(vals!=null){
					LOG.debug("login via codetu pour "+codetu+" => "+vals[0]);
					return vals[0];
				}
			}
			return null;
		} catch (Exception e) {
			LOG.error("probleme de récupération du login depuis le codetu via le ldap. ",e);
			return null;
		}
	}
}
