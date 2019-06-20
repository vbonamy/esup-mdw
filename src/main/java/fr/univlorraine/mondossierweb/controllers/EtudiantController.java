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
package fr.univlorraine.mondossierweb.controllers;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.apache.axis.AxisFault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import fr.univlorraine.mondossierweb.GenericUI;
import fr.univlorraine.mondossierweb.beans.Adresse;
import fr.univlorraine.mondossierweb.beans.BacEtatCivil;
import fr.univlorraine.mondossierweb.beans.Etudiant;
import fr.univlorraine.mondossierweb.beans.Inscription;
import fr.univlorraine.mondossierweb.converters.EmailConverterInterface;
import fr.univlorraine.mondossierweb.entities.apogee.DiplomeApogee;
import fr.univlorraine.mondossierweb.services.apogee.ComposanteService;
import fr.univlorraine.mondossierweb.services.apogee.ComposanteServiceImpl;
import fr.univlorraine.mondossierweb.services.apogee.DiplomeApogeeService;
import fr.univlorraine.mondossierweb.services.apogee.DiplomeApogeeServiceImpl;
import fr.univlorraine.mondossierweb.services.apogee.ElementPedagogiqueService;
import fr.univlorraine.mondossierweb.services.apogee.InscriptionService;
import fr.univlorraine.mondossierweb.services.apogee.InscriptionServiceImpl;
import fr.univlorraine.mondossierweb.services.apogee.MultipleApogeeService;
import fr.univlorraine.mondossierweb.services.apogee.SsoApogeeService;
import fr.univlorraine.mondossierweb.utils.PropertyUtils;
import fr.univlorraine.mondossierweb.utils.Utils;
import gouv.education.apogee.commun.client.utils.WSUtils;
import gouv.education.apogee.commun.client.ws.administratifmetier.AdministratifMetierServiceInterface;
import gouv.education.apogee.commun.client.ws.etudiantmetier.EtudiantMetierServiceInterface;
import gouv.education.apogee.commun.client.ws.pedagogiquemetier.PedagogiqueMetierServiceInterface;
import gouv.education.apogee.commun.transverse.dto.administratif.CursusExterneDTO;
import gouv.education.apogee.commun.transverse.dto.administratif.CursusExternesEtTransfertsDTO;
import gouv.education.apogee.commun.transverse.dto.administratif.InsAdmAnuDTO2;
import gouv.education.apogee.commun.transverse.dto.administratif.InsAdmEtpDTO3;
import gouv.education.apogee.commun.transverse.dto.etudiant.AdresseDTO2;
import gouv.education.apogee.commun.transverse.dto.etudiant.AdresseMajDTO;
import gouv.education.apogee.commun.transverse.dto.etudiant.CommuneMajDTO;
import gouv.education.apogee.commun.transverse.dto.etudiant.CoordonneesDTO2;
import gouv.education.apogee.commun.transverse.dto.etudiant.CoordonneesMajDTO;
import gouv.education.apogee.commun.transverse.dto.etudiant.IdentifiantsEtudiantDTO2;
import gouv.education.apogee.commun.transverse.dto.etudiant.IndBacDTO;
import gouv.education.apogee.commun.transverse.dto.etudiant.InfoAdmEtuDTO2;
import gouv.education.apogee.commun.transverse.dto.etudiant.TypeHebergementCourtDTO;
import gouv.education.apogee.commun.transverse.exception.WebBaseException;

/**
 * Gestion de l'étudiant dont on consulte le dossier
 */
@Component
public class EtudiantController {

	private Logger LOG = LoggerFactory.getLogger(EtudiantController.class);


	/* Injections */
	@Resource
	private transient ApplicationContext applicationContext;
	@Resource
	private transient Environment environment;
	/** {@link DiplomeApogeeServiceImpl} */
	@Resource
	private DiplomeApogeeService diplomeService;
	/** {@link InscriptionServiceImpl} */
	@Resource
	private InscriptionService inscriptionService;
	/** {@link ComposanteServiceImpl} */
	@Resource
	private ComposanteService composanteService;
	@Resource
	private SsoApogeeService ssoApogeeService;
	@Resource
	private ElementPedagogiqueService elementPedagogiqueService;
	@Resource
	private transient UiController uiController;
	@Resource
	private transient UserController userController;
	@Resource
	private transient ConfigController configController;

	@Resource(name="${emailConverter.implementation}")
	private transient EmailConverterInterface emailConverter;



	/**
	 * proxy pour faire appel aux infos concernant un étudiant.
	 */
	private EtudiantMetierServiceInterface monProxyEtu;

	/**
	 * proxy pour faire appel aux infos administratives du WS .
	 */
	private AdministratifMetierServiceInterface monProxyAdministratif;

	/**
	 * proxy pour faire appel aux infos sur les résultats du WS .
	 */
	private PedagogiqueMetierServiceInterface monProxyPedagogique;

	@Resource
	private MultipleApogeeService multipleApogeeService;

	@Resource
	private transient SsoController ssoController;

	/*@Resource
	private SessionController sessionController;*/


	public boolean isEtudiantExiste(String codetu){

		if(monProxyEtu==null)
			monProxyEtu = (EtudiantMetierServiceInterface) WSUtils.getService(WSUtils.ETUDIANT_SERVICE_NAME);
		try {
			//informations générales :
			IdentifiantsEtudiantDTO2 idetu;

			if (!PropertyUtils.isRecupMailAnnuaireApogee()) {
				//idetu = monProxyEtu.recupererIdentifiantsEtudiant_v2(codetu, null, null, null, null, null, null, null, null, "N");
				idetu = monProxyEtu.recupererIdentifiantsEtudiant_v2(codetu, null, null, null, null, null, null, null, "N");
			} else {
				idetu = monProxyEtu.recupererIdentifiantsEtudiant_v2(codetu, null, null, null, null, null, null, null, "O");
			}
			if(idetu!=null && idetu.getCodInd()!=0 && StringUtils.hasText(idetu.getCodInd().toString())){
				return true;
			}
			return false;
		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			/*if (ex.getNature().equals("remoteerror")){
				LOG.error("Probleme avec le WS lors de la recherche de l'état-civil pour etudiant dont codetu est : " + codetu,ex);
			}else{
				LOG.info("Probleme avec le WS lors de la recherche de l'état-civil pour etudiant dont codetu est : " + codetu,ex);
			}*/
			return false;
		} catch (Exception ex) {
			//LOG.error("Probleme lors de la recherche de l'état-civil pour etudiant dont codetu est : " + codetu,ex);
			return false;
		}
	}

	public void recupererEtatCivil() {

		if(GenericUI.getCurrent().getEtudiant()!=null && StringUtils.hasText(GenericUI.getCurrent().getEtudiant().getCod_etu())){
			if(monProxyEtu==null){
				monProxyEtu = (EtudiantMetierServiceInterface) WSUtils.getService(WSUtils.ETUDIANT_SERVICE_NAME);
			}
			if(monProxyAdministratif==null){
				monProxyAdministratif = (AdministratifMetierServiceInterface)  WSUtils.getService(WSUtils.ADMINISTRATIF_SERVICE_NAME);
			}
			try {
				//informations générales :
				IdentifiantsEtudiantDTO2 idetu;

				if (!PropertyUtils.isRecupMailAnnuaireApogee()) {
					idetu = monProxyEtu.recupererIdentifiantsEtudiant_v2(GenericUI.getCurrent().getEtudiant().getCod_etu(), null, null, null, null, null, null, null, "N");
				} else {
					idetu = monProxyEtu.recupererIdentifiantsEtudiant_v2(GenericUI.getCurrent().getEtudiant().getCod_etu(), null, null, null, null, null, null, null, "O");
				}

				GenericUI.getCurrent().getEtudiant().setCod_ind(idetu.getCodInd().toString());

				//Gestion des codine null
				//if(idetu.getNumeroINE() != null && idetu.getCleINE() != null ){
				if(idetu.getNumeroINE() != null ){
					//GenericUI.getCurrent().getEtudiant().setCod_nne(idetu.getNumeroINE() + idetu.getCleINE());
					GenericUI.getCurrent().getEtudiant().setCod_nne(idetu.getNumeroINE());
				}else{
					GenericUI.getCurrent().getEtudiant().setCod_nne("");
				}

				//Pour ne renseigner la photo que si elle n'est pas renseignée.
				//GenericUI.getCurrent().getEtudiant().setPhoto(photo.getUrlPhoto(GenericUI.getCurrent().getEtudiant().getCod_ind(),GenericUI.getCurrent().getEtudiant().getCod_etu()));
				GenericUI.getCurrent().getEtudiant().setPhoto(GenericUI.getCurrent().getPhotoProvider().getUrlPhoto(GenericUI.getCurrent().getEtudiant().getCod_ind(),GenericUI.getCurrent().getEtudiant().getCod_etu(), userController.isEnseignant(),userController.getCurrentUserName()));

				if (!PropertyUtils.isRecupMailAnnuaireApogee()) {
					// on passe par emailConverter pour récupérer l'e-mail.
					GenericUI.getCurrent().getEtudiant().setEmail(emailConverter.getMail(GenericUI.getCurrent().getEtudiant().getCod_etu()));
				} else {
					//on récupérer l'e-mail grâce au WS.
					GenericUI.getCurrent().getEtudiant().setEmail(idetu.getEmailAnnuaire());
				}



				//InfoAdmEtuDTO iaetu = monProxyEtu.recupererInfosAdmEtu(GenericUI.getCurrent().getEtudiant().getCod_etu());
				InfoAdmEtuDTO2 iaetu = monProxyEtu.recupererInfosAdmEtu_v2(GenericUI.getCurrent().getEtudiant().getCod_etu());

				//Utilisant du nom patronymique
				GenericUI.getCurrent().getEtudiant().setNom( iaetu.getPrenom1()+ " "+iaetu.getNomPatronymique());

				//Si afichage utilisant le nom usuel
				if(PropertyUtils.getTypeAffichageNomEtatCivil().equals(PropertyUtils.AFFICHAGE_NOM_BASIQUE)
						&& iaetu.getNomUsuel() != null && !iaetu.getNomUsuel().equals("")){
					GenericUI.getCurrent().getEtudiant().setNom(iaetu.getPrenom1()+ " "+iaetu.getNomUsuel());

				}else if(PropertyUtils.getTypeAffichageNomEtatCivil().equals(PropertyUtils.AFFICHAGE_NOM_STANDARD)
						&& iaetu.getNomUsuel() != null && !iaetu.getNomUsuel().equals("") && !iaetu.getNomUsuel().equals(iaetu.getNomPatronymique())){
					//Si affichage avec nom patronymique ET usuel et si nom usuel non null et différent du nom patronymique
					GenericUI.getCurrent().getEtudiant().setNom(iaetu.getPrenom1()+ " "+iaetu.getNomPatronymique()+ " ("+iaetu.getNomUsuel()+")");

				}


				//informations sur la naissance :
				//la nationalité:
				if (iaetu.getNationaliteDTO() != null) {
					GenericUI.getCurrent().getEtudiant().setNationalite(iaetu.getNationaliteDTO().getLibNationalite());
				} else {
					GenericUI.getCurrent().getEtudiant().setNationalite("");
				}
				//la date de naissance:
				if (iaetu.getDateNaissance() != null) {
					Date d = iaetu.getDateNaissance().getTime();
					GenericUI.getCurrent().getEtudiant().setDatenaissance(Utils.formatDateToString(d));
				} else {
					GenericUI.getCurrent().getEtudiant().setDatenaissance("");
				}
				//la ville de naissance:
				GenericUI.getCurrent().getEtudiant().setLieunaissance(iaetu.getLibVilleNaissance());

				//récupération du département ou du pays de naissance:
				if (iaetu.getDepartementNaissance() != null ) {
					GenericUI.getCurrent().getEtudiant().setDepartementnaissance(iaetu.getDepartementNaissance().getLibDept());
				} else {
					if (iaetu.getPaysNaissance() != null) {
						GenericUI.getCurrent().getEtudiant().setDepartementnaissance(iaetu.getPaysNaissance().getLibPay());
					} else {
						GenericUI.getCurrent().getEtudiant().setDepartementnaissance("");
					}
				}

				//informations sur l'inscription universitaire :
				GenericUI.getCurrent().getEtudiant().setAnneeInscriptionUniversitaire(iaetu.getAnneePremiereInscEnsSup());

				if (iaetu.getEtbPremiereInscUniv() != null) {
					GenericUI.getCurrent().getEtudiant().setEtablissement(iaetu.getEtbPremiereInscUniv().getLibEtb());
				} else {
					GenericUI.getCurrent().getEtudiant().setEtablissement("");
				}


				//informations sur le(s) bac(s) :
				if (GenericUI.getCurrent().getEtudiant().getListeBac() != null && GenericUI.getCurrent().getEtudiant().getListeBac().size() > 0) {
					GenericUI.getCurrent().getEtudiant().getListeBac().clear();
				} else {
					GenericUI.getCurrent().getEtudiant().setListeBac(new ArrayList<BacEtatCivil>());
				}

				GenericUI.getCurrent().setAnneeUnivEnCours(multipleApogeeService.getAnneeEnCours());
				LOG.debug("anneeUnivEnCours : "+GenericUI.getCurrent().getAnneeUnivEnCours());
				try{
					InsAdmAnuDTO2[] iaad2 = monProxyAdministratif.recupererIAAnnuelles_v2(GenericUI.getCurrent().getEtudiant().getCod_etu(), GenericUI.getCurrent().getAnneeUnivEnCours(), "ARE");
					if(iaad2!=null){
						LOG.debug("nb ia pour annee en cours : "+iaad2.length);
						boolean insOkTrouvee=false;
						for(int i=0; i<iaad2.length;i++){
							InsAdmAnuDTO2 iaad = iaad2[i];
							//Si IA non annulée
							if(!insOkTrouvee && iaad!=null && iaad.getEtatIaa()!=null && iaad.getEtatIaa().getCodeEtatIAA()!=null && !iaad.getEtatIaa().getCodeEtatIAA().equals("A") ){
								insOkTrouvee=true;

								//recuperer le code cat sociale
								if( multipleApogeeService.isBoursier(GenericUI.getCurrent().getEtudiant().getCod_ind(), GenericUI.getCurrent().getAnneeUnivEnCours())){
									GenericUI.getCurrent().getEtudiant().setBoursier(true);
								}

								//recuperer le statut
								if(iaad.getStatut()!= null && iaad.getStatut().getCode()!=null){
									GenericUI.getCurrent().getEtudiant().setStatut(iaad.getStatut().getCode());
								}

								//recupérer le témoin d'affiliation à la sécu
								if(iaad.getTemoinAffiliationSS()!=null && iaad.getTemoinAffiliationSS().equals("O")){
									GenericUI.getCurrent().getEtudiant().setAffilieSso(true);
								}
								
								//recupérer le régime d'inscription
								if(iaad.getRegimeIns()!=null && StringUtils.hasText(iaad.getRegimeIns().getLibRgi())){
									GenericUI.getCurrent().getEtudiant().setRegimeIns(iaad.getRegimeIns().getLibRgi());
								}


								GenericUI.getCurrent().getEtudiant().setInscritPourAnneeEnCours(true);
								//Si témoin aménagement d'étude valué à O
								if(iaad.getTemRgmAmgEtuIAA()!=null && iaad.getTemRgmAmgEtuIAA().equals("O")){
									GenericUI.getCurrent().getEtudiant().setTemAmenagementEtude(true);
								}
							}
						}
						if(!insOkTrouvee){
							GenericUI.getCurrent().getEtudiant().setInscritPourAnneeEnCours(false);
						}

						GenericUI.getCurrent().getEtudiant().setTemSalarie(multipleApogeeService.isSalarie(GenericUI.getCurrent().getEtudiant().getCod_ind(), GenericUI.getCurrent().getAnneeUnivEnCours()));

						//Si catégorie socio-professionnelle renseignée
						/*if(iaad.getCatSocProfEtu()!=null && iaad.getCatSocProfEtu().getCodeCategorie()!=null){
							String codeCatSocPro = iaad.getCatSocProfEtu().getCodeCategorie();
							//test si la catégorie n'est pas une catégorie de non salarié
							if(!codeCatSocPro.equals("81") && !codeCatSocPro.equals("82") &&
									!codeCatSocPro.equals("99") &&
									!codeCatSocPro.equals("A") ){
								GenericUI.getCurrent().getEtudiant().setTemSalarie(true);
							}
						}*/
					}else{
						GenericUI.getCurrent().getEtudiant().setInscritPourAnneeEnCours(false);
					}
				} catch (WebBaseException ex) {
					GenericUI.getCurrent().getEtudiant().setInscritPourAnneeEnCours(false);
					LOG.info("Aucune IA remontée par le WS pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu()+" pour l'année "+GenericUI.getCurrent().getAnneeUnivEnCours());
				} catch (AxisFault axf) {
					GenericUI.getCurrent().getEtudiant().setInscritPourAnneeEnCours(false);
					LOG.info("Aucune IA remontée par le WS pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu()+" pour l'année "+GenericUI.getCurrent().getAnneeUnivEnCours());
				}

				IndBacDTO[] bacvo = iaetu.getListeBacs();
				if (bacvo != null) {
					for (int i = 0; i < bacvo.length; i++) {
						IndBacDTO bac = bacvo[i];
						if (bac != null) {
							BacEtatCivil bec = new BacEtatCivil();

							bec.setLib_bac(bac.getLibelleBac());
							bec.setCod_bac(bac.getCodBac());
							bec.setDaa_obt_bac_iba(bac.getAnneeObtentionBac());

							if (bac.getDepartementBac() != null ) {
								bec.setCod_dep(bac.getDepartementBac().getLibDept());
							} else {
								bec.setCod_dep("");
							}
							if (bac.getMentionBac() != null) {
								bec.setCod_mnb(bac.getMentionBac().getLibMention());
							} else {
								bec.setCod_mnb("");
							}
							if (bac.getTypeEtbBac() != null) {
								bec.setCod_tpe(bac.getTypeEtbBac().getLibLongTpe());
							} else { 
								bec.setCod_tpe("");
							}
							if (bac.getEtbBac() != null) {
								bec.setCod_etb(bac.getEtbBac().getLibEtb());
							} else {
								bec.setCod_etb("");
							}
							GenericUI.getCurrent().getEtudiant().getListeBac().add(bec);
						}
					}
				} else {
					LOG.info("Probleme avec le WS: AUCUN BAC RETOURNE, lors de la recherche de l'état-civil pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu());
					BacEtatCivil bec = new BacEtatCivil();
					bec.setLib_bac("/");
					GenericUI.getCurrent().getEtudiant().getListeBac().add(bec);
				}

				//On recupere les numeros d'anonymat
				GenericUI.getCurrent().getEtudiant().setNumerosAnonymat(multipleApogeeService.getNumeroAnonymat(GenericUI.getCurrent().getEtudiant().getCod_etu(), getAnneeUnivEnCours(GenericUI.getCurrent())));

				//On vérifie si l'étudiant est interdit de consultation de ses notes
				List<String> lcodesBloquant = configController.getListeCodesBlocageAffichageNotes();
				//Si on a paramétré des codes bloquant
				if(lcodesBloquant!=null && lcodesBloquant.size()>0){
					//Récupération des éventuels blocage pour l'étudiant
					List<String> lblo = multipleApogeeService.getListeCodeBlocage(GenericUI.getCurrent().getEtudiant().getCod_etu());
					// Si l'étudiant a des blocages
					if(lblo!=null && lblo.size()>0){
						//Parcours des blocage
						for(String codblo : lblo){
							//Si le blocage est dans la liste des blocages configurés comme bloquant
							if(codblo != null && lcodesBloquant.contains(codblo)){
								//étudiant non autorise a consulter ses notes
								GenericUI.getCurrent().getEtudiant().setNonAutoriseConsultationNotes(true);
							}
						}
					}
				}

				//On appel recupererAdresses pour récupérer le mail perso et le tel portable de l'étudiant
				recupererAdresses();

			} catch (WebBaseException ex) {
				//Si on est dans un cas d'erreur non expliqué
				if (ex.getNature().equals("remoteerror")){
					LOG.error("Probleme avec le WS lors de la recherche de l'état-civil pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu()+" "+ex.getNature(),ex);
				}else{
					LOG.info("Probleme avec le WS lors de la recherche de l'état-civil pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu()+" "+ex.getNature(),ex);
				}
				//On met l'étudiant à null pour remonter le problème
				GenericUI.getCurrent().setEtudiant(null);
			} catch (AxisFault axf) {
				//LOG.info("Probleme avec le WS lors de la recherche de l'état-civil pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu()+" "+axf.getMessage(),axf);
				axf.printStackTrace();
				//On met l'étudiant à null pour remonter le problème
				GenericUI.getCurrent().setEtudiant(null);
			} catch (Exception ex) {
				LOG.error("Probleme lors de la recherche de l'état-civil pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),ex);
				//On met l'étudiant à null pour remonter le problème
				GenericUI.getCurrent().setEtudiant(null);
			}
		}

	}


	public void recupererAdresses() {

		if(GenericUI.getCurrent().getEtudiant()!=null && StringUtils.hasText(GenericUI.getCurrent().getEtudiant().getCod_etu())){
			if(monProxyAdministratif==null){
				monProxyAdministratif = (AdministratifMetierServiceInterface) WSUtils.getService(WSUtils.ADMINISTRATIF_SERVICE_NAME);
			}
			try{
				String[] annees =  monProxyAdministratif.recupererAnneesIa(GenericUI.getCurrent().getEtudiant().getCod_etu(), null);

				if(annees!=null){
					//récupération de l'année la plus récente
					String annee = "0";
					for(int i=0; i<annees.length;i++){
						if (Integer.parseInt(annees[i])>Integer.parseInt(annee)){
							annee = annees[i];
						}
					}

					//récupération des coordonnées :
					CoordonneesDTO2 cdto = monProxyEtu.recupererAdressesEtudiant_v2(GenericUI.getCurrent().getEtudiant().getCod_etu(), annee, "N");

					//récupération des adresses, annuelle et fixe :
					annee = cdto.getAnnee();
					GenericUI.getCurrent().getEtudiant().setEmailPerso(cdto.getEmail());
					GenericUI.getCurrent().getEtudiant().setTelPortable(cdto.getNumTelPortable());


					AdresseDTO2 ada = cdto.getAdresseAnnuelle();
					AdresseDTO2 adf = cdto.getAdresseFixe();

					if (ada != null) {
						Adresse adresseAnnuelle=new Adresse();


						adresseAnnuelle.setAnnee(Utils.getAnneeUniversitaireEnCours(annee));
						//informations sur l'adresse annuelle :
						if (ada.getLibAde() != null) {
							adresseAnnuelle.setAdresseetranger(ada.getLibAde());
							adresseAnnuelle.setCodePostal("");
							adresseAnnuelle.setVille("");
						} else {
							adresseAnnuelle.setAdresseetranger(null);
							if (ada.getCommune() != null) {
								adresseAnnuelle.setCodePostal(ada.getCommune().getCodePostal());
								adresseAnnuelle.setVille(ada.getCommune().getNomCommune());
							} else {
								adresseAnnuelle.setCodePostal("");
								adresseAnnuelle.setVille("");
							}
						}

						//TypeHebergementCourtDTO th = ada.getTypeHebergement();
						TypeHebergementCourtDTO th = cdto.getTypeHebergement();
						if (th != null) {
							//adresseAnnuelle.setType(th.getLibTypeHebergement());
							adresseAnnuelle.setType(th.getCodTypeHebergement());
						} else {
							adresseAnnuelle.setType("");
						}
						adresseAnnuelle.setAdresse1(ada.getLibAd1());
						adresseAnnuelle.setAdresse2(ada.getLibAd2());
						adresseAnnuelle.setAdresse3(ada.getLibAd3());
						adresseAnnuelle.setNumerotel(ada.getNumTel());
						if (ada.getPays() != null) {
							adresseAnnuelle.setPays(ada.getPays().getLibPay());
							adresseAnnuelle.setCodPays(ada.getPays().getCodPay());
						} else {
							adresseAnnuelle.setPays("");
						}

						GenericUI.getCurrent().getEtudiant().setAdresseAnnuelle(adresseAnnuelle);
					}
					if (adf != null) {

						Adresse adresseFixe=new Adresse();

						//informations sur l'adresse fixe :
						adresseFixe.setAdresse1(adf.getLibAd1());
						adresseFixe.setAdresse2(adf.getLibAd2());
						adresseFixe.setAdresse3(adf.getLibAd3());
						adresseFixe.setNumerotel(adf.getNumTel());

						if (adf.getLibAde() != null) {
							adresseFixe.setAdresseetranger(adf.getLibAde());
							adresseFixe.setCodePostal("");
							adresseFixe.setVille("");
						} else {
							adresseFixe.setAdresseetranger(null);
							if (adf.getCommune() != null ) {
								adresseFixe.setCodePostal(adf.getCommune().getCodePostal());
								adresseFixe.setVille(adf.getCommune().getNomCommune());
							} else {
								adresseFixe.setCodePostal("");
								adresseFixe.setVille("");
							}
						}
						if (adf.getPays() != null) {
							adresseFixe.setPays(adf.getPays().getLibPay());
							adresseFixe.setCodPays(adf.getPays().getCodPay());
						} else {
							adresseFixe.setPays("");
						}

						GenericUI.getCurrent().getEtudiant().setAdresseFixe(adresseFixe);
					}
				}else{
					LOG.info("Probleme lors de la recherche des annees d'IA pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu());
				}
			} catch (WebBaseException ex) {
				//Si on est dans un cas d'erreur non expliqué
				if (ex.getNature().equals("remoteerror")){
					LOG.error("Probleme avec le WS lors de la recherche de l'adresse pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),ex);
				}else{
					LOG.info("Probleme avec le WS lors de la recherche de l'adresse pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),ex);
				}
			} catch (AxisFault axf) {
				LOG.info("Probleme avec le WS lors de la recherche de l'adresse pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),axf);
			} catch (Exception ex) {
				LOG.error("Probleme lors de la recherche de l'adresse pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),ex);
			}

		}
	}

	/**
	 * va chercher et renseigne les informations concernant les inscriptions de 
	 * l'étudiant via le WS de l'Amue.
	 * @return true si tout c'est bien passé, false sinon
	 */
	public void recupererInscriptions() {
		try {
			if(GenericUI.getCurrent().getEtudiant().getLinsciae()!=null){
				GenericUI.getCurrent().getEtudiant().getLinsciae().clear();
			}else{
				GenericUI.getCurrent().getEtudiant().setLinsciae(new LinkedList<Inscription>());
			}

			if(GenericUI.getCurrent().getEtudiant().getLinscdac()!=null){
				GenericUI.getCurrent().getEtudiant().getLinscdac().clear();
			}else{
				GenericUI.getCurrent().getEtudiant().setLinscdac(new LinkedList<Inscription>());
			}


			GenericUI.getCurrent().getEtudiant().setLibEtablissement(multipleApogeeService.getLibEtablissementDef());

			//cursus au sein de l'université:

			InsAdmEtpDTO3[] insdtotab = monProxyAdministratif.recupererIAEtapes_v3(GenericUI.getCurrent().getEtudiant().getCod_etu(), "toutes", "ARE", "ARE");

			if(insdtotab!=null){
				for (int i = 0; i < insdtotab.length; i++) {
					Inscription insc = new Inscription();
					InsAdmEtpDTO3 insdto = insdtotab[i];

					//on test si l'inscription n'est pas annulée:
					if (insdto.getEtatIae()!=null && insdto.getEtatIae().getCodeEtatIAE()!=null && insdto.getEtatIae().getCodeEtatIAE().equals("E")){

						//récupération de l'année
						int annee = new Integer(insdto.getAnneeIAE());
						int annee2 = annee + 1;
						insc.setCod_anu(annee + "/" + annee2);

						//récupération des informations sur l'étape
						insc.setCod_etp(insdto.getEtape().getCodeEtp());
						insc.setCod_vrs_vet(insdto.getEtape().getVersionEtp());
						insc.setLib_etp(insdto.getEtape().getLibWebVet());

						//récupération des informations sur le diplôme
						insc.setCod_dip(insdto.getDiplome().getCodeDiplome());
						insc.setVers_dip(insdto.getDiplome().getVersionDiplome());
						insc.setLib_dip(insdto.getDiplome().getLibWebVdi());

						//récupération des informations sur la composante
						insc.setCod_comp(insdto.getComposante().getCodComposante());
						//insc.setLib_comp(insdto.getComposante().getLibComposante());
						insc.setLib_comp(composanteService.getLibelleComposante(insc.getCod_comp()));

						//récupération de l'état en règle de l'inscription
						if(insdto.getInscriptionPayee().equals(Utils.LIBELLE_WS_INSCRIPTION_PAYEE)){
							insc.setEstEnRegle(true);
						}else{
							insc.setEstEnRegle(false);
						}

						//récupération de l'état de l'inscription
						if(insdto.getEtatIae()!=null && StringUtils.hasText(insdto.getEtatIae().getCodeEtatIAE())){
							insc.setEtatIae(insdto.getEtatIae().getCodeEtatIAE());
							if(insdto.getEtatIae().getCodeEtatIAE().equals(Utils.ETAT_IAE_EN_COURS)){
								insc.setEstEnCours(true);
							}else{
								insc.setEstEnCours(false);
							}
						}else{
							insc.setEtatIae(null);
							insc.setEstEnCours(false);
						}

						//ajout de l'inscription à la liste
						GenericUI.getCurrent().getEtudiant().getLinsciae().add(0, insc);
					}
				}
			}

			//Autres cursus : 

			CursusExternesEtTransfertsDTO ctdto = monProxyAdministratif.recupererCursusExterne(GenericUI.getCurrent().getEtudiant().getCod_etu());

			if (ctdto != null) {
				CursusExterneDTO[] listeCursusExt = ctdto.getListeCursusExternes();
				for (int i = 0; i < listeCursusExt.length; i++) {

					Inscription insc = new Inscription();

					CursusExterneDTO cext = listeCursusExt[i];

					int annee = new Integer(cext.getAnnee());
					int annee2 = annee + 1;
					insc.setCod_anu(annee + "/" + annee2);

					if (cext.getEtablissement() != null && cext.getTypeAutreDiplome() != null) {
						insc.setLib_etb(cext.getEtablissement().getLibEtb());
						// 24/04/2012 utilisation du libTypeDiplome a la place du CodeTypeDiplome
						insc.setCod_dac(cext.getTypeAutreDiplome().getLibTypeDiplome());
						insc.setLib_cmt_dac(cext.getCommentaire());
						if (cext.getTemObtentionDip() != null && cext.getTemObtentionDip().equals("N") ) {
							insc.setRes("AJOURNE");
						} else {
							insc.setRes("OBTENU");
						}

						GenericUI.getCurrent().getEtudiant().getLinscdac().add(0, insc);
					}
				}
			}


			//première inscription universitaire : 
			InfoAdmEtuDTO2 iaetu = monProxyEtu.recupererInfosAdmEtu_v2(GenericUI.getCurrent().getEtudiant().getCod_etu());
			if (iaetu != null) {
				GenericUI.getCurrent().getEtudiant().setAnneePremiereInscrip(iaetu.getAnneePremiereInscUniv());
				GenericUI.getCurrent().getEtudiant().setEtbPremiereInscrip(iaetu.getEtbPremiereInscUniv().getLibEtb());
			}

			GenericUI.getCurrent().setRecuperationWsInscriptionsOk(true);

			//Si l'étudiant est inscrit pour l'année en cours
			if(GenericUI.getCurrent().getEtudiant().isInscritPourAnneeEnCours()){
				//Tentative de récupération des informations relatives à l'affiliation à la sécurité sociale
				try{
					GenericUI.getCurrent().getEtudiant().setRecuperationInfosAffiliationSsoOk(ssoController.recupererInfoAffiliationSso(getAnneeUnivEnCours(GenericUI.getCurrent()),GenericUI.getCurrent().getEtudiant()));
				} catch(Exception e){
					LOG.info("Probleme lors de la recuperer des Info AffiliationSso pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),e);
				}

				//Tentative de récupération des informations relatives à la quittance des droits payés
				try{
					GenericUI.getCurrent().getEtudiant().setRecuperationInfosQuittanceOk(ssoController.recupererInfoQuittance(getAnneeUnivEnCours(GenericUI.getCurrent()),GenericUI.getCurrent().getEtudiant()));
				} catch(Exception e){
					LOG.info("Probleme lors de la recuperer des Info Quittance pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),e);
				}
			}


		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			if (ex.getNature().equals("remoteerror")){
				LOG.error("Probleme avec le WS lors de la recherche des inscriptions pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(), ex);
			}else{
				LOG.info("Probleme avec le WS lors de la recherche des inscriptions pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(), ex);
			}
			GenericUI.getCurrent().setRecuperationWsInscriptionsOk(false);
		} catch(Exception ex) {
			if(GenericUI.getCurrent().getEtudiant()!=null){
				LOG.error("Probleme lors de la recherche des inscriptions pour etudiant dont codetu est : " + GenericUI.getCurrent().getEtudiant().getCod_etu(),ex);
			}else{
				LOG.error("Probleme lors de la recherche des inscriptions pour etudiant ",ex);
			}
			GenericUI.getCurrent().setRecuperationWsInscriptionsOk(false);
		}
	}





	/**
	 * va chercher et renseigne les informations concernant le calendrier des examens
	 */
	public void recupererCalendrierExamens() {
		if(GenericUI.getCurrent()!=null && GenericUI.getCurrent().getEtudiant()!=null && StringUtils.hasText(GenericUI.getCurrent().getEtudiant().getCod_ind())){
			GenericUI.getCurrent().getEtudiant().setCalendrier(multipleApogeeService.getCalendrierExamens(GenericUI.getCurrent().getEtudiant().getCod_ind(),configController.isAffDetailExamen()));
			GenericUI.getCurrent().getEtudiant().setCalendrierRecupere(true);
		}
	}


	public String getAnneeUnivEnCours(GenericUI ui) {
		if(ui!=null){
			if(ui.getAnneeUnivEnCours()==null){
				ui.setAnneeUnivEnCours(multipleApogeeService.getAnneeEnCours());
			}
			return ui.getAnneeUnivEnCours();
		}
		return multipleApogeeService.getAnneeEnCours();
	}

	public String getAnneeUnivEnCoursToDisplay(GenericUI ui) {
		int annee = Integer.parseInt(getAnneeUnivEnCours(ui));
		return annee+"/"+(annee+1);

	}



	public String getFormationEnCours(String codetu){
		return inscriptionService.getFormationEnCours(codetu);
	}




	public boolean proposerAttestationAffiliationSSO(Inscription ins, Etudiant etu){

		// autoriser ou non la generation de l'attestation
		if (!configController.isAttestationAffiliationSSO()) {
			return false;
		}
		// autoriser ou non les personnels à imprimer les attestations.
		if ( !configController.isAttestSsoAutorisePersonnel() && userController.isEnseignant()) {
			return false;
		}
		String codAnuIns=ins.getCod_anu().substring(0, 4);
		if (!codAnuIns.equals(getAnneeUnivEnCours(GenericUI.getCurrent()))) {
			return false;
		}
		//si l'IAE n'est pas en règle
		if (!ins.isEstEnRegle()) {
			return false;
		}
		//si l'IAE n'est pas à l'état 'E'
		if (!ins.isEstEnCours()) {
			return false;
		}
		//Si pas affilié à la sécu
		if(!etu.isAffilieSso()){
			return false;
		}
		//interdit l'édition si on n'a pas réussi à récupérer les informations
		if(!etu.isRecuperationInfosAffiliationSsoOk()){
			return false;
		}

		return true;
	}

	public boolean proposerQuittanceDroitsPayes(Inscription ins, Etudiant etu){

		// autoriser ou non la generation de la quittance
		if (!configController.isQuittanceDroitsPayes()) {
			return false;
		}
		// autoriser ou non les personnels à imprimer les quittance
		if ( !configController.isQuittanceDroitsPayesAutorisePersonnel() && userController.isEnseignant()) {
			return false;
		}
		String codAnuIns=ins.getCod_anu().substring(0, 4);
		if (!codAnuIns.equals(getAnneeUnivEnCours(GenericUI.getCurrent()))) {
			return false;
		}
		//interdit l'edition de quittance si l'inscription n'est pas payée
		if(!ins.isEstEnRegle()){
			return false;
		}
		//interdit l'edition de quittance si l'inscription n'est pas "en cours"
		if(!ins.isEstEnCours()){
			return false;
		}
		//interdit l'édition si on n'a pas réussi à récupérer les informations
		if(!etu.isRecuperationInfosQuittanceOk()){
			return false;
		}

		return true;
	}

	public boolean proposerCertificat(Inscription ins, Etudiant etu, boolean mobile) {

		// autoriser ou non la generation de certificats de scolarite.
		if (!configController.isCertificatScolaritePDF()) {
			return false;
		}
		// autoriser ou non les personnels à imprimer les certificats.
		if ( !configController.isCertScolAutorisePersonnel() && userController.isEnseignant()) {
			return false;
		}
		String codAnuIns=ins.getCod_anu().substring(0, 4);
		// si on autorise l'édition de certificat de scolarité uniquement pour l'année en cours.
		if ((mobile || !configController.isCertificatScolariteTouteAnnee()) && !codAnuIns.equals(getAnneeUnivEnCours(GenericUI.getCurrent()))) {
			return false;
		}
		List<String> listeCertScolTypDiplomeDesactive=configController.getListeCertScolTypDiplomeDesactive();
		if ( listeCertScolTypDiplomeDesactive!=null && !listeCertScolTypDiplomeDesactive.isEmpty()) {
			// interdit les certificats pour certains types de diplomes
			DiplomeApogee dip = diplomeService.findDiplome(ins.getCod_dip());
			if(dip!=null && StringUtils.hasText(dip.getCodTpdEtb())){
				if (listeCertScolTypDiplomeDesactive.contains(dip.getCodTpdEtb())) {
					return false;
				}
			}
		}
		//interdit l'edition de certificat pour les étudiants si l'inscription n'est pas payée
		if ( !ins.isEstEnRegle() && userController.isEtudiant()){
			return false;
		}
		//interdit l'édition de certificat pour les étudiants si il reste des pièces justificatives non validées
		if(userController.isEtudiant() && !configController.isCertificatScolaritePiecesNonValidees()){
			//Si il reste des PJ non valides
			if(multipleApogeeService.getNbPJnonValides(etu.getCod_ind(), codAnuIns)>0){
				return false;
			}
		}
		//interdit l'edition de certificat pour les étudiants si l'inscription en cours est une cohabitation
		List<String> listeCertScolProfilDesactive=configController.getListeCertScolProfilDesactive();
		if ( listeCertScolProfilDesactive!=null && !listeCertScolProfilDesactive.isEmpty()) {
			// interdit les certificats pour certains types de diplomes
			String profil = inscriptionService.getProfil(codAnuIns, etu.getCod_ind());

			if (listeCertScolProfilDesactive.contains(profil)) {
				return false;
			}
		}
		//interdit l'édition de certificat pour les étudiants si l'inscription correspond à un code CGE désactivé
		List<String> listeCertScolCGEDesactive=configController.getListeCertScolCGEDesactive();
		if (listeCertScolCGEDesactive!=null && !listeCertScolCGEDesactive.isEmpty()) {
			// interdit les certificats pour certains code CGE
			String cge = inscriptionService.getCgeFromCodIndIAE(codAnuIns, etu.getCod_ind(), ins.getCod_etp(), ins.getCod_vrs_vet());

			if (listeCertScolCGEDesactive.contains(cge)) {
				return false;
			}
		}
		//interdit l'édition de certificat pour les étudiants si l'inscription correspond à un code composante désactivé
		List<String> listeCertScolCmpDesactive=configController.getListeCertScolCmpDesactive();
		if ( listeCertScolCmpDesactive!=null && !listeCertScolCmpDesactive.isEmpty()) {
			// interdit les certificats pour certains code composante
			String cmp = inscriptionService.getCmpFromCodIndIAE(codAnuIns, etu.getCod_ind(), ins.getCod_etp(), ins.getCod_vrs_vet());

			if (listeCertScolCmpDesactive.contains(cmp)) {
				return false;
			}
		}


		//interdit l'édition de certificat pour les étudiants dont le statut est dans la liste des exclusions
		List<String> listeCertScolStatutDesactive=configController.getListeCertScolStatutDesactive();
		if ( listeCertScolStatutDesactive!=null && !listeCertScolStatutDesactive.isEmpty()) {

			// interdit les certificats pour certains types de statut
			String statut = inscriptionService.getStatut(codAnuIns, etu.getCod_ind());

			if (statut!=null && listeCertScolStatutDesactive.contains(statut)) {
				return false;
			}
		}

		//interdit l'édition de certificat pour les étudiants si le témoin edition carte n'est pas coche
		if(userController.isEtudiant() && configController.isCertificatScolariteCarteEditee()){
			String temoinCarteEdit = multipleApogeeService.getTemoinEditionCarte(etu.getCod_ind(), codAnuIns);
			if ((temoinCarteEdit==null)||(!temoinCarteEdit.contains(Utils.TEMOIN_EDITION_CARTE_EDITEE))){
				return false;
			}
		}


		return true;
	}


	public List<String> updateContact(String telephone, String mail,String codetu) {
		List<String> retour = new LinkedList<String>();
		boolean erreur = false;
		String message = "";
		if(StringUtils.hasText(telephone) && !Pattern.matches("[0-9[. ]]*", telephone)){
			message = applicationContext.getMessage("modificationContact.erreur.tel", null, Locale.getDefault());
			retour.add(message);
			erreur = true;
		}
		if(StringUtils.hasText(mail) && !Pattern.matches("^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]{2,}[.][a-zA-Z]{2,4}$", mail)){
			message = applicationContext.getMessage("modificationContact.erreur.mail", null, Locale.getDefault());
			retour.add(message);
			erreur = true;
		}

		//Si aucune erreur sur les données insérées
		if(!erreur){
			boolean succes = false;
			//On insere dans Apogée
			if(monProxyEtu==null){
				monProxyEtu = (EtudiantMetierServiceInterface) WSUtils.getService(WSUtils.ETUDIANT_SERVICE_NAME);
			}
			if(monProxyAdministratif==null){
				monProxyAdministratif = (AdministratifMetierServiceInterface) WSUtils.getService(WSUtils.ADMINISTRATIF_SERVICE_NAME);
			}
			try {
				//recup de l'ancienne et modif dessus:
				String[] annees =  monProxyAdministratif.recupererAnneesIa(codetu, null);
				//récupération de l'année la plus récente
				String annee = "0";
				for(int i=0; i<annees.length;i++){
					if (Integer.parseInt(annees[i])>Integer.parseInt(annee)){
						annee = annees[i];
					}
				}
				CoordonneesDTO2 cdto = monProxyEtu.recupererAdressesEtudiant_v2(codetu, annee , "N");


				AdresseMajDTO adanmaj = new AdresseMajDTO();
				AdresseMajDTO adfixmaj = new AdresseMajDTO();

				adanmaj.setLibAd1(cdto.getAdresseAnnuelle().getLibAd1());
				adanmaj.setLibAd2(cdto.getAdresseAnnuelle().getLibAd2());
				adanmaj.setLibAd3(cdto.getAdresseAnnuelle().getLibAd3());
				adanmaj.setNumTel(cdto.getAdresseAnnuelle().getNumTel());
				adanmaj.setCodPays(cdto.getAdresseAnnuelle().getPays().getCodPay());
				if (cdto.getAdresseAnnuelle().getCommune()!=null) {
					CommuneMajDTO comanmaj = new CommuneMajDTO();
					comanmaj.setCodeInsee(cdto.getAdresseAnnuelle().getCommune().getCodeInsee());
					comanmaj.setCodePostal(cdto.getAdresseAnnuelle().getCommune().getCodePostal());
					adanmaj.setCommune(comanmaj);
				}
				if(StringUtils.hasText(cdto.getAdresseAnnuelle().getLibAde())){
					adanmaj.setLibAde(cdto.getAdresseAnnuelle().getLibAde());
				}



				adfixmaj.setLibAd1(cdto.getAdresseFixe().getLibAd1());
				adfixmaj.setLibAd2(cdto.getAdresseFixe().getLibAd2());
				adfixmaj.setLibAd3(cdto.getAdresseFixe().getLibAd3());
				adfixmaj.setNumTel(cdto.getAdresseFixe().getNumTel());
				adfixmaj.setCodPays(cdto.getAdresseFixe().getPays().getCodPay());
				if (cdto.getAdresseFixe().getCommune()!=null) {
					CommuneMajDTO comfixmaj = new CommuneMajDTO();
					comfixmaj.setCodeInsee(cdto.getAdresseFixe().getCommune().getCodeInsee());
					comfixmaj.setCodePostal(cdto.getAdresseFixe().getCommune().getCodePostal());
					adfixmaj.setCommune(comfixmaj);
				}
				if(StringUtils.hasText(cdto.getAdresseFixe().getLibAde())){
					adfixmaj.setLibAde(cdto.getAdresseFixe().getLibAde());
				}


				CoordonneesMajDTO cdtomaj = new CoordonneesMajDTO();
				cdtomaj.setAnnee(annee);
				cdtomaj.setTypeHebergement(cdto.getTypeHebergement().getCodTypeHebergement());
				cdtomaj.setEmail(mail);
				cdtomaj.setNumTelPortable(telephone);
				cdtomaj.setAdresseAnnuelle(adanmaj);
				cdtomaj.setAdresseFixe(adfixmaj);

				LOG.debug("==== MAJ ADRESSE ==="+cdto.getAnnee()+" "+cdto.getTypeHebergement().getCodTypeHebergement());
				monProxyEtu.mettreAJourAdressesEtudiant(cdtomaj, codetu);

				succes = true;
			} catch (WebBaseException ex) {
				LOG.error("Probleme avec le WS lors de la maj des adresses de l'etudiant dont codetu est : " + codetu,ex);
			} catch (Exception ex) {
				LOG.error("Probleme avec le WS lors de la maj des adresses de l'etudiant dont codetu est : " + codetu,ex);
			}

			if (!succes) {
				message = applicationContext.getMessage("modificationContact.erreur.ws", null, Locale.getDefault());
				retour.add(message);
			}else{
				retour.add("OK");
			}
		}
		return retour;
	}


}
