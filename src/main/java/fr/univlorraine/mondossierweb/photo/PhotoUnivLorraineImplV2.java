/**
 *
 *  ESUP-Portail MONDOSSIERWEB - Copyright (c) 2016 ESUP-Portail consortium
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package fr.univlorraine.mondossierweb.photo;

import java.util.Date;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import fr.univlorraine.mondossierweb.converters.LoginCodeEtudiantConverterInterface;


@Scope(value="session", proxyMode=ScopedProxyMode.DEFAULT)
@Component(value="photoUnivLorraineImplV2")
public class PhotoUnivLorraineImplV2 implements IPhoto {

	private Logger LOG = LoggerFactory.getLogger(PhotoUnivLorraineImplV2.class);
	
	@Resource(name="${loginFromCodetu.implementation}")
	private LoginCodeEtudiantConverterInterface loginCodeEtudiantConverter;

	/**
	 * le token JWT du user
	 */
	private String userTokenJWT;
	
	private String photoUrl;
	
	private String tokenUrl;
	
	private String clientIdHeader;
	
	private String  apiKeyHeader;

	private String  loginHeader;

	private String clientId;

	private String clientSecret;


	public String getPhotoUrl() {
		if(photoUrl==null){
			photoUrl=System.getProperty("context.param.photoserver.urlphoto");
		}
		return photoUrl;
	}
	
	public String getTokenUrl() {
		if(tokenUrl==null){
			tokenUrl=System.getProperty("context.param.photoserver.urltoken");
		}
		return tokenUrl;
	}

	public String getClientIdHeader() {
		if(clientIdHeader==null){
			clientIdHeader=System.getProperty("context.param.photoserver.clientidheader");
		}
		return clientIdHeader;
	}
	
	public String getApiKeyHeader() {
		if(apiKeyHeader==null){
			apiKeyHeader=System.getProperty("context.param.photoserver.apikeyheader");
		}
		return apiKeyHeader;
	}
	
	public String getLoginHeader() {
		if(loginHeader==null){
			loginHeader=System.getProperty("context.param.photoserver.loginheader");
		}
		return loginHeader;
	}

	public String getClientId() {
		if(clientId==null){
			clientId=System.getProperty("context.param.photoserver.clientid");
		}
		return clientId;
	}

	public String getClientSecret() {
		if(clientSecret==null){
			clientSecret=System.getProperty("context.param.photoserver.clientsecret");
		}
		return clientSecret;
	}

	@Override
	public String getUrlPhoto(String cod_ind, String cod_etu, boolean isUtilisateurEnseignant, String loginUser) {
		checkTokenForUser(loginUser);

		// Récupération de la photo
		return getPhoto(userTokenJWT, loginCodeEtudiantConverter.getLoginFromCodEtu(cod_etu));
	}


	@Override
	public String getUrlPhotoTrombinoscopePdf(String cod_ind, String cod_etu, boolean isUtilisateurEnseignant, String loginUser) {
		checkTokenForUser(loginUser);

		// Récupération de la photo
		return getPhoto(userTokenJWT, loginCodeEtudiantConverter.getLoginFromCodEtu(cod_etu));
	}

	private void checkTokenForUser(String loginUser) {
		// Si le token du user est null ou expiré
		if(userTokenJWT==null || isTokenExpired(userTokenJWT)) {
			userTokenJWT = getToken(loginUser);
		}
	}


	private String getToken(String login) {
		String url = getTokenUrl();

		// Headers
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.add(getClientIdHeader(), getClientId());
		requestHeaders.add(getApiKeyHeader(), getClientSecret());
		if(login != null) {
			requestHeaders.add(getLoginHeader(), login);
		}

		//Body
		LinkedMultiValueMap<String, Object>  params = new LinkedMultiValueMap<>();

		// Request
		HttpEntity<?> request = new HttpEntity<>(params , requestHeaders);

		LOG.debug("GET TOKEN : "+url+" request : "+request);
		
		// Http Call 
		RestTemplate rt = new RestTemplate();
		try {
			ResponseEntity<String> response = rt.exchange(url, HttpMethod.GET, request, String.class);
			// Si appel OK
			if(response !=null && response.getStatusCode().equals(HttpStatus.OK)) {
				return response.getBody();
			} else {
				LOG.warn("Une erreur est survenue lors de la récupération du token JWT du serveur photo "+login+" Error Response => " + ( response == null ? "null" : response.getStatusCode().toString()));
			}
		} catch (Exception e) {
			LOG.error("Une erreur est survenue lors de la récupération du token JWT du serveur photo "+login,e);
		}
		return null;
	}

	


	private String getPhoto(String token, String login) {
		String url = getPhotoUrl() + "/" + login;

		// Headers
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.add("Authorization", "Bearer "+token);
		
		//Body
		LinkedMultiValueMap<String, Object>  params = new LinkedMultiValueMap<>();

		// Request
		HttpEntity<?> request = new HttpEntity<>(params , requestHeaders);

		LOG.debug("GET PHOTO : "+url+" request : "+request);
		
		// Http Call 
		RestTemplate rt = new RestTemplate();
		try {
			ResponseEntity<String> response = rt.exchange(url, HttpMethod.GET, request, String.class);
			// Si appel OK
			if(response !=null && response.getStatusCode().equals(HttpStatus.OK)) {
				return "data:image/png;base64, "+ response.getBody();
			} else {
				LOG.warn("Une erreur est survenue lors de la récupération du token JWT du serveur photo "+login+" Error Response => " + ( response == null ? "null" : response.getStatusCode().toString()));
			}
		} catch (Exception e) {
			LOG.error("Une erreur est survenue lors de la récupération du token JWT du serveur photo "+login,e);
		}
		return null;
	}

	

	@Override
	public boolean isOperationnel() {
		return true;
	}

	private Boolean isTokenExpired(String token) {
		DecodedJWT decodedToken  = JWT.decode(token);
		if (decodedToken != null) {
			LOG.debug("token expires at : "+decodedToken.getExpiresAt());
			return decodedToken.getExpiresAt().before(new Date());
		}
		LOG.debug("token null");
		return true;
	}
	
}
