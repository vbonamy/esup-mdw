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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Resource;

import org.apache.axis.AxisFault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import fr.univlorraine.mondossierweb.GenericUI;
import fr.univlorraine.mondossierweb.beans.CacheIP;
import fr.univlorraine.mondossierweb.beans.CacheResultatsElpEpr;
import fr.univlorraine.mondossierweb.beans.CacheResultatsVdiVet;
import fr.univlorraine.mondossierweb.beans.Diplome;
import fr.univlorraine.mondossierweb.beans.ElementPedagogique;
import fr.univlorraine.mondossierweb.beans.Etape;
import fr.univlorraine.mondossierweb.beans.Etudiant;
import fr.univlorraine.mondossierweb.beans.Resultat;
import fr.univlorraine.mondossierweb.services.apogee.ElementPedagogiqueService;
import fr.univlorraine.mondossierweb.utils.PropertyUtils;
import fr.univlorraine.mondossierweb.utils.Utils;
import gouv.education.apogee.commun.client.utils.WSUtils;
import gouv.education.apogee.commun.client.ws.pedagogiquemetier.PedagogiqueMetierServiceInterface;
import gouv.education.apogee.commun.transverse.dto.pedagogique.ContratPedagogiqueResultatElpEprDTO5;
import gouv.education.apogee.commun.transverse.dto.pedagogique.ContratPedagogiqueResultatVdiVetDTO2;
import gouv.education.apogee.commun.transverse.dto.pedagogique.EpreuveElpDTO2;
import gouv.education.apogee.commun.transverse.dto.pedagogique.EtapeResVdiVetDTO2;
import gouv.education.apogee.commun.transverse.dto.pedagogique.ResultatElpDTO3;
import gouv.education.apogee.commun.transverse.dto.pedagogique.ResultatEprDTO;
import gouv.education.apogee.commun.transverse.dto.pedagogique.ResultatVdiDTO;
import gouv.education.apogee.commun.transverse.dto.pedagogique.ResultatVetDTO;
import gouv.education.apogee.commun.transverse.exception.WebBaseException;

/**
 * Gestion de la récupération des notes et résultats
 */
@Component(value="resultatController")
public class ResultatController {

	private Logger LOG = LoggerFactory.getLogger(ResultatController.class);

	/* Injections */
	@Resource
	private transient ApplicationContext applicationContext;

	@Resource
	private ElementPedagogiqueService elementPedagogiqueService;

	@Resource
	private transient ConfigController configController;

	@Resource
	private transient EtudiantController etudiantController;

	/**
	 * proxy pour faire appel aux infos sur les résultats du WS .
	 */
	private PedagogiqueMetierServiceInterface monProxyPedagogique;

	/**
	 * va chercher et renseigne les notes de
	 * l'étudiant via le WS de l'Amue.
	 */
	public void recupererNotesEtResultats(Etudiant e) {
		if(monProxyPedagogique==null){
			monProxyPedagogique = (PedagogiqueMetierServiceInterface) WSUtils.getService(WSUtils.PEDAGOGIQUE_SERVICE_NAME, PropertyUtils.getApoWsUsername(),PropertyUtils.getApoWsPassword());
		}

		try {
			e.getDiplomes().clear();
			e.getEtapes().clear();

			String temoin = configController.getTemoinNotesEtudiant();
			if(temoin == null || temoin.equals("")){
				temoin="T";
			}

			String temoinEtatIae = configController.getTemoinEtatIaeNotesEtudiant();
			if(temoinEtatIae == null || temoinEtatIae.equals("")){
				temoinEtatIae="E";
			}

			String sourceResultat = PropertyUtils.getSourceResultats();
			if(sourceResultat == null || sourceResultat.equals("")){
				sourceResultat="Apogee";
			}


			// VR 09/11/2009 : Verif annee de recherche si sourceResultat = apogee-extraction :
			// Si different annee en cours => sourceResultat = Apogee
			if(sourceResultat.compareTo("Apogee-extraction")==0){
				// On recupere les resultats dans cpdto avec sourceResultat=Apogee
				sourceResultat="Apogee";
				ContratPedagogiqueResultatVdiVetDTO2[] cpdtoResult = monProxyPedagogique.recupererContratPedagogiqueResultatVdiVet_v2(e.getCod_etu(), "toutes", sourceResultat, temoin, "toutes", "tous",temoinEtatIae);

				// Puis dans cpdtoExtract avec sourceResultat=Apogee-extraction
				temoin=null;
				sourceResultat="Apogee-extraction";
				ContratPedagogiqueResultatVdiVetDTO2[] cpdtoExtract;
				try {
					cpdtoExtract = monProxyPedagogique.recupererContratPedagogiqueResultatVdiVet_v2(e.getCod_etu(), "toutes", sourceResultat, temoin, "toutes", "tous",temoinEtatIae);
				} catch (Exception ex) {
					cpdtoExtract = null;
				}

				// Et on fusionne cpdtoResult et cpdtoExtract
				ArrayList<ContratPedagogiqueResultatVdiVetDTO2> cpdtoAl = new ArrayList<ContratPedagogiqueResultatVdiVetDTO2>();
				for (int i = 0; i < cpdtoResult.length; i++ ) {
					String anneeResultat = getAnneeContratPedagogiqueResultatVdiVet(cpdtoResult[i]);
					if (anneeResultat != null && !utilisationExtractionApogee(anneeResultat)) {
						cpdtoAl.add(cpdtoResult[i]);
					}
				}
				if (cpdtoExtract!=null) {
					for (int i = 0; i < cpdtoExtract.length; i++ ) {
						String anneeResultat = getAnneeContratPedagogiqueResultatVdiVet(cpdtoExtract[i]);
						if(anneeResultat != null && utilisationExtractionApogee(anneeResultat)){
							cpdtoAl.add(cpdtoExtract[i]);
						}
					}
				}
				ContratPedagogiqueResultatVdiVetDTO2[] cpdto = cpdtoAl.toArray(new ContratPedagogiqueResultatVdiVetDTO2[ cpdtoAl.size() ]);
				setNotesEtResultats(e, cpdto);

			} else {

				ContratPedagogiqueResultatVdiVetDTO2[] cpdto = monProxyPedagogique.recupererContratPedagogiqueResultatVdiVet_v2(e.getCod_etu(), "toutes", sourceResultat, temoin, "toutes", "tous", temoinEtatIae);
				setNotesEtResultats(e, cpdto);
			}


		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			if (ex.getNature().equals("remoteerror")){
				LOG.error(ex.getLastErrorMsg()+" Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}else{
				LOG.info(ex.getLastErrorMsg()+" Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}
		} catch (AxisFault axf) {
			LOG.info("Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),axf);
		} catch (Exception ex) {
			LOG.error("Probleme lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
		}

	}


	/**
	 * va chercher et renseigne les notes de
	 * l'étudiant à destination d'un enseignant via le WS de l'Amue.
	 */
	public void recupererNotesEtResultatsEnseignant(Etudiant e) {

		if(monProxyPedagogique==null){
			monProxyPedagogique = (PedagogiqueMetierServiceInterface) WSUtils.getService(WSUtils.PEDAGOGIQUE_SERVICE_NAME, PropertyUtils.getApoWsUsername(),PropertyUtils.getApoWsPassword());
		}

		if(e!=null && StringUtils.hasText(e.getCod_etu())){
			try {
				e.getDiplomes().clear();
				e.getEtapes().clear();

				String temoin = configController.getTemoinNotesEnseignant();
				if(temoin == null || temoin.equals("")){
					temoin="AET";
				}

				String temoinEtatIae = configController.getTemoinEtatIaeNotesEnseignant();
				if(temoinEtatIae == null || temoinEtatIae.equals("")){
					temoinEtatIae="E";
				}

				String sourceResultat = PropertyUtils.getSourceResultats();
				if(sourceResultat == null || sourceResultat.equals("")){
					sourceResultat="Apogee";
				}


				// VR 09/11/2009 : Verif annee de recherche si sourceResultat = apogee-extraction :
				// Si different annee en cours => sourceResultat = Apogee
				if(sourceResultat.compareTo("Apogee-extraction")==0){
					// On recupere les resultats dans cpdto avec sourceResultat=Apogee
					sourceResultat="Apogee";
					ContratPedagogiqueResultatVdiVetDTO2[] cpdtoResult = monProxyPedagogique.recupererContratPedagogiqueResultatVdiVet_v2(e.getCod_etu(), "toutes", sourceResultat, temoin, "toutes", "tous",temoinEtatIae);
					// Puis dans cpdtoExtract avec sourceResultat=Apogee-extraction pour l'année en cours
					temoin=null;
					sourceResultat="Apogee-extraction";
					ContratPedagogiqueResultatVdiVetDTO2[] cpdtoExtract;
					try {
						cpdtoExtract = monProxyPedagogique.recupererContratPedagogiqueResultatVdiVet_v2(e.getCod_etu(), "toutes", sourceResultat, temoin, "toutes", "tous",temoinEtatIae);
					} catch (Exception ex) {
						cpdtoExtract = null;
					}

					// Et on fusionne cpdtoResult et cpdtoExtract
					ArrayList<ContratPedagogiqueResultatVdiVetDTO2> cpdtoAl = new ArrayList<ContratPedagogiqueResultatVdiVetDTO2>();
					for (int i = 0; i < cpdtoResult.length; i++ ) {
						String anneeResultat = getAnneeContratPedagogiqueResultatVdiVet(cpdtoResult[i]);
						if (anneeResultat != null && !utilisationExtractionApogee(anneeResultat)) {
							cpdtoAl.add(cpdtoResult[i]);
						}
					}
					if (cpdtoExtract!=null) {
						for (int i = 0; i < cpdtoExtract.length; i++ ) {
							String anneeResultat = getAnneeContratPedagogiqueResultatVdiVet(cpdtoExtract[i]);
							if(anneeResultat != null && utilisationExtractionApogee(anneeResultat)){
								cpdtoAl.add(cpdtoExtract[i]);
							}
						}
					}
					ContratPedagogiqueResultatVdiVetDTO2[] cpdto = cpdtoAl.toArray(new ContratPedagogiqueResultatVdiVetDTO2[ cpdtoAl.size() ]);
					setNotesEtResultats(e, cpdto);

				} else {

					ContratPedagogiqueResultatVdiVetDTO2[] cpdto = monProxyPedagogique.recupererContratPedagogiqueResultatVdiVet_v2(e.getCod_etu(), "toutes", sourceResultat, temoin, "toutes", "tous", temoinEtatIae);
					setNotesEtResultats(e, cpdto);
				}

			} catch (WebBaseException ex) {
				//Si on est dans un cas d'erreur non expliqué
				if (ex.getNature().equals("remoteerror")){
					LOG.error(ex.getLastErrorMsg()+" Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
				}else{
					LOG.info(ex.getLastErrorMsg()+" Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
				}
			} catch (AxisFault axf) {
				LOG.info("Probleme lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),axf);
			} catch (Exception ex) {
				LOG.error("Probleme lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}
		}
	}



	private String getAnneeContratPedagogiqueResultatVdiVet(ContratPedagogiqueResultatVdiVetDTO2 ct) {
		//Si l'année du contrat est non null
		if(ct.getAnnee()!=null){
			// on retourne l'année du contrat
			return ct.getAnnee();
		}
		//Si l'année du premier item de la liste "etapes" est non null
		if(ct.getEtapes()!=null && ct.getEtapes().length>0 && ct.getEtapes()[0]!=null && ct.getEtapes()[0].getCodAnu()!=null){
			//On retourne l'année du premier item de la liste "etapes"
			return ct.getEtapes()[0].getCodAnu();
		}
		return null;
	}


	/**
	 * renseigne les attributs concernant les notes et résultats obtenus.
	 * @param e
	 * @param cpdto
	 */
	public void setNotesEtResultats(Etudiant e, ContratPedagogiqueResultatVdiVetDTO2[] resultatVdiVet) {
		try {

			if(e.getDiplomes()!=null){
				e.getDiplomes().clear();
			}else{
				e.setDiplomes(new LinkedList<Diplome>());
			}

			if(e.getEtapes()!=null){
				e.getEtapes().clear();
			}else{
				e.setEtapes(new LinkedList<Etape>());
			}

			//Si on a configure pour toujours afficher le rang, on affichera les rangs de l'étudiant.
			e.setAfficherRang(configController.isAffRangEtudiant());

			if(resultatVdiVet!=null && resultatVdiVet.length>0){
				for (int i = 0; i < resultatVdiVet.length; i++ ) {
					//information sur le diplome:
					ContratPedagogiqueResultatVdiVetDTO2 rdto = resultatVdiVet[i];

					if(rdto.getDiplome() != null){
						Diplome d = new Diplome();

						d.setLib_web_vdi(rdto.getDiplome().getLibWebVdi());
						d.setCod_dip(rdto.getDiplome().getCodDip());
						d.setCod_vrs_vdi(rdto.getDiplome().getCodVrsVdi().toString());

						int annee2 = new Integer(rdto.getAnnee()) + 1;


						d.setAnnee(rdto.getAnnee() + "/" + annee2);
						//information sur les résultats obtenus au diplome:
						ResultatVdiDTO[] tabres = rdto.getResultatVdi();

						if (tabres != null && tabres.length > 0) {


							for (int j = 0; j < tabres.length; j++ ) {
								Resultat r = new Resultat();
								ResultatVdiDTO res = tabres[j];

								r.setSession(res.getSession().getLibSes());
								if(res.getNatureResultat() != null && res.getNatureResultat().getCodAdm() != null && res.getNatureResultat().getCodAdm().equals("0")){
									//on est en Admissibilité à l'étape.Pas en admission.
									//on le note pour que ce soit plus clair pour l'étudiant
									r.setNote(res.getNatureResultat().getLibAdm());
								}

								//recuperation de la mention
								if(res.getMention() != null){
									r.setCodMention(res.getMention().getCodMen());
									r.setLibMention(res.getMention().getLibMen());
								}

								String result="";
								if( res.getTypResultat()!=null){
									result= res.getTypResultat().getCodTre();
									r.setAdmission(result);
								}
								if (res.getNotVdi() != null) {
									r.setNote(res.getNotVdi().toString());
									//ajout pour note Jury
									if(res.getNotPntJurVdi() != null && !res.getNotPntJurVdi().equals(new BigDecimal(0))){
										r.setNote(r.getNote()+"(+"+res.getNotPntJurVdi()+")");
									}
								} else {
									if (result.equals("DEF")) {
										r.setNote("DEF");
									}
								}

								//Gestion du barème:
								if(res.getBarNotVdi() != null){
									r.setBareme(res.getBarNotVdi());
								}


								//ajout de la signification du résultat dans la map
								if ((result != null && !result.equals("")) && !e.getSignificationResultats().containsKey(r.getAdmission())) {
									e.getSignificationResultats().put(r.getAdmission(), res.getTypResultat().getLibTre());
								}

								//ajout du résultat au diplome:
								d.getResultats().add(r);
								if(res.getNbrRngEtuVdi() != null && !res.getNbrRngEtuVdi().equals("")){
									d.setRang(res.getNbrRngEtuVdi()+"/"+res.getNbrRngEtuVdiTot());
									//On indique si on affiche le rang du diplome.
									d.setAfficherRang(configController.isAffRangEtudiant());

								}
							}
							//ajout du diplome si on a au moins un résultat
							//e.getDiplomes().add(0, d);
						}
						e.getDiplomes().add(0, d);
					}
					//information sur les etapes:
					EtapeResVdiVetDTO2[] etapes = rdto.getEtapes();
					if (etapes != null && etapes.length > 0) {

						for (int j = 0; j < etapes.length; j++ ) {
							EtapeResVdiVetDTO2 etape = etapes[j];


							Etape et = new Etape();
							int anneeEtape = new Integer(etape.getCodAnu());
							et.setAnnee(anneeEtape + "/" + (anneeEtape + 1));
							et.setCode(etape.getEtape().getCodEtp());
							et.setVersion(etape.getEtape().getCodVrsVet().toString());
							et.setLibelle(etape.getEtape().getLibWebVet());

							//ajout 16/02/2012 pour WS exposés pour la version mobile en HttpInvoker
							if(rdto.getDiplome()!= null){
								et.setCod_dip(rdto.getDiplome().getCodDip());
								et.setVers_dip(rdto.getDiplome().getCodVrsVdi());
							}

							//résultats de l'étape:
							ResultatVetDTO[] tabresetape = etape.getResultatVet();
							if (tabresetape != null && tabresetape.length > 0) {
								for (int k = 0; k < tabresetape.length; k++ ) {
									ResultatVetDTO ret = tabresetape[k];
									Resultat r = new Resultat();
									if(!ret.getEtatDelib().getCodEtaAvc().equals("T")) {
										et.setDeliberationTerminee(false);
									} else {
										et.setDeliberationTerminee(true);
									}

									r.setSession(ret.getSession().getLibSes());
									if(ret.getNatureResultat() != null && ret.getNatureResultat().getCodAdm()!= null && ret.getNatureResultat().getCodAdm().equals("0")){
										//on est en Admissibilité à l'étape.Pas en admission.
										//on le note pour que ce soit plus clair pour l'étudiant
										r.setNote(ret.getNatureResultat().getLibAdm());

									}
									//recuperation de la mention
									if(ret.getMention() != null){
										r.setCodMention(ret.getMention().getCodMen());
										r.setLibMention(ret.getMention().getLibMen());
									}

									String result="";
									if(ret.getTypResultat() != null){
										result = ret.getTypResultat().getCodTre();
										r.setAdmission(result);
									}
									if (ret.getNotVet() != null) {
										r.setNote(ret.getNotVet().toString());
										//ajout note jury
										if(ret.getNotPntJurVet() != null && !ret.getNotPntJurVet().equals(new BigDecimal(0))){
											r.setNote(r.getNote()+"(+"+ret.getNotPntJurVet()+")");
										}

									} else {
										if (result.equals("DEF")) {
											r.setNote("DEF");
										}
									}

									//Gestion du barème:
									if(ret.getBarNotVet() != null){
										r.setBareme(ret.getBarNotVet());
									}

									//ajout de la signification du résultat dans la map
									if (result != null && !result.equals("") && !e.getSignificationResultats().containsKey(r.getAdmission())) {
										e.getSignificationResultats().put(r.getAdmission(), ret.getTypResultat().getLibTre());
									}


									//ajout du résultat par ordre de code session (Juillet 2014)
									//ajout du resultat en fin de liste
									//et.getResultats().add(r);
									try{
										int session = Integer.parseInt(ret.getSession().getCodSes());
										if(et.getResultats().size()>0 && et.getResultats().size()>=session){
											//ajout du résultat à la bonne place dans la liste
											et.getResultats().add((session-1),r);
										}else{
											//ajout du résultat en fin de liste
											et.getResultats().add(r);
										}
									}catch(Exception excep){
										et.getResultats().add(r);
									}

									//ajout du rang
									if(ret.getNbrRngEtuVet() != null && !ret.getNbrRngEtuVet().equals("")){
										et.setRang(ret.getNbrRngEtuVet()+"/"+ret.getNbrRngEtuVetTot());
										//On calcule si on affiche ou non le rang.
										boolean cetteEtapeDoitEtreAffiche=false;

										List<String> codesAutorises = configController.getListeCodesEtapeAffichageRang();
										if(codesAutorises!=null && codesAutorises.contains(et.getCode())){
											cetteEtapeDoitEtreAffiche=true;
										}
										if(configController.isAffRangEtudiant() || cetteEtapeDoitEtreAffiche){
											//On affichera le rang de l'étape.
											et.setAfficherRang(true);
											//On remonte au niveau de l'étudiant qu'on affiche le rang
											e.setAfficherRang(true);
										}
									}

								}
							}

							//ajout de l'étape a la liste d'étapes de l'étudiant:
							//e.getEtapes().add(0, et);
							//en attendant la maj du WS :
							insererEtapeDansListeTriee(e, et);


						}
					}

				}
			}
		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			if (ex.getNature().equals("remoteerror")){
				LOG.error("Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}else{
				LOG.info("Probleme avec le WS lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}
		} catch (Exception ex) {
			LOG.error("Probleme lors de la recherche des notes et résultats pour etudiant dont codetu est : " + e.getCod_etu(),ex);
		}

	}




	private void insererEtapeDansListeTriee(Etudiant e, Etape et){

		boolean insere = false;
		int rang = 0;
		int anneeEtape = new Integer(et.getAnnee().substring(0, 4));
		while(!insere && rang < e.getEtapes().size()){

			int anneeEtapeEnCours = new Integer(e.getEtapes().get(rang).getAnnee().substring(0, 4));
			if(anneeEtape > anneeEtapeEnCours){
				e.getEtapes().add(rang, et);
				insere = true;
			}
			rang++;
		} 
		if(!insere){
			e.getEtapes().add(et);
		}
	}


	/**
	 * Récupère les données retournées par le WS et les trie pour les afficher
	 * @param e etudiant
	 * @param et etape
	 * @param reedto objet retourne par le WS
	 * @param temoinEtatDelib
	 */
	public void setNotesElpEpr(Etudiant e, Etape et, ContratPedagogiqueResultatElpEprDTO5[] reedto,String temoinEtatDelib, int anneeResultat, boolean sourceExtractionApogee) {
		try {

			e.getElementsPedagogiques().clear();
			//liste intermédiaire pour trié les éléments pédagogiques:
			List<ElementPedagogique> liste1 = new ArrayList<ElementPedagogique>();


			if (reedto != null && reedto.length > 0) {
				//On parcourt les ELP:
				for (int i = 0; i < reedto.length; i++ ) {

					ElementPedagogique elp = new ElementPedagogique();
					elp.setCode(reedto[i].getElp().getCodElp());
					elp.setLevel(reedto[i].getRngElp());
					elp.setCodElpSup(reedto[i].getCodElpSup());
					elp.setLibelle(reedto[i].getElp().getLibElp());
					elp.setAnnee("");
					elp.setEpreuve(false);

					elp.setNote1("");
					elp.setBareme1(0);
					elp.setRes1("");
					elp.setNote2("");
					elp.setBareme2(0);
					elp.setRes2("");
					elp.setEcts("");
					elp.setTemFictif(reedto[i].getElp().getNatureElp().getTemFictif());
					elp.setTemSemestre("N");
					elp.setTemSemestre(reedto[i].getElp().getNatureElp().getTemSemestre());
					elp.setEtatDelib("");
					
					
					//Récupération des crédits ects de référence
					String creditEctsElp = null;
					//Si on a récupéré un crédit ECTS de référence
					if(reedto[i].getElp().getNbrCrdElp()!=null && reedto[i].getElp().getNbrCrdElp().toString()!=null && !reedto[i].getElp().getNbrCrdElp().toString().equals("")){
						creditEctsElp = reedto[i].getElp().getNbrCrdElp().toString();
					}

					//vrai si l'ELP est il dans un etat de delib qui nous convient en session1:
					boolean elpEtatDelibS1OK=false;

					//vrai si l'ELP est il dans un etat de delib qui nous convient en session2:
					boolean elpEtatDelibS2OK=false;

					if(!sourceExtractionApogee || elpAvecResultats(reedto[i])){

						if (reedto[i].getElp().getNatureElp().getCodNel().equals("FICM")) {
							//utile pour ne pas afficher les FICM par la suite
							elp.setAnnee("FICM");
						}

						//contient l'année de la PRC si les résultats sont obtenus en PRC
						String anneePrc = null;

						//On s'occupe des résultats :
						ResultatElpDTO3[] relpdto = reedto[i].getResultatsElp();
						if (relpdto != null && relpdto.length > 0) {
							//on parcourt les résultats pour l'ELP:
							for (int j = 0; j < relpdto.length; j++ ) {
								if(relpdto[j] != null && relpdto[j].getEtatDelib() != null && relpdto[j].getEtatDelib().getCodEtaAvc()!= null)
									elp.setEtatDelib(relpdto[j].getEtatDelib().getCodEtaAvc());

								//on affiche pas les résultats d'admissibilité
								if(configController.isAffResAdmissibilite() || relpdto[j].getNatureResultat()==null || relpdto[j].getNatureResultat().getCodAdm()== null || !relpdto[j].getNatureResultat().getCodAdm().equals("0")){
									//29/01/10
									//On récupère les notes si l'ELP est dans un état de delibération compris dans la liste des témoins paramétrés.
									if(relpdto[j].getEtatDelib()==null ||  temoinEtatDelib.contains(relpdto[j].getEtatDelib().getCodEtaAvc())){

										int codsession = 0;
										if(relpdto[j].getSession() != null){
											codsession = new Integer(relpdto[j].getSession().getCodSes());
										}else{
											//Pour info, on arrive ici car on peut etre en VAC: validation d'acquis
										}

										String result = null;

										//le résultat:
										if (relpdto[j].getTypResultat() != null ) {
											result = relpdto[j].getTypResultat().getCodTre();
										}

										//Test sur la session traitée
										if (codsession < 2) {
											//l'elp est dans un état de delibération compris dans la liste des témoins paramétrés.
											elpEtatDelibS1OK=true;

											//1er session  : juin
											if (relpdto[j].getNotElp() != null && !relpdto[j].getNotElp().equals("null")) {
												elp.setNote1(relpdto[j].getNotElp().toString());
												if(relpdto[j].getNotPntJurElp()!= null && !relpdto[j].getNotPntJurElp().equals(new BigDecimal(0))){
													elp.setNote1(elp.getNote1()+"(+"+relpdto[j].getNotPntJurElp()+")");
												}

											} 
											if ((elp.getNote1() == null || (elp.getNote1() != null && elp.getNote1().equals(""))) && result != null && result.equals("DEF")) {
												elp.setNote1("DEF");
											}

											//Gestion du barème:
											if(relpdto[j].getBarNotElp() != null){
												elp.setBareme1(relpdto[j].getBarNotElp());
											}

											//ajout du rang si pas déjà renseigné via la session de juin.
											if(relpdto[j].getNbrRngEtuElp() != null && !relpdto[j].getNbrRngEtuElp().equals("")
													&& (elp.getRang()==null || elp.getRang().equals(""))){
												elp.setRang(relpdto[j].getNbrRngEtuElp()+"/"+relpdto[j].getNbrRngEtuElpTot());
											}

											//on récupère l'année car si année!=null c'est un PRC  si pas déjà renseigné via la session de juin.
											if(relpdto[j].getCodAnu()!=null && !relpdto[j].getCodAnu().equals("")
													&& (elp.getAnnee()==null || elp.getAnnee().equals(""))){
												elp.setAnnee(relpdto[j].getCodAnu());
												anneePrc = relpdto[j].getCodAnu();
											}

											//Récupération des crédits ECTS avant la version 5.20.laa
											//On recupere les crédits ECTS si valué et si pas déjà renseigné via la session de juin.
											/*if(relpdto[j].getNbrCrdElp()!= null && relpdto[j].getNbrCrdElp().toString()!=null && !relpdto[j].getNbrCrdElp().toString().equals("")
													&& (elp.getEcts()==null || elp.getEcts().equals(""))){
												String anneeECTS=relpdto[j].getCodAnu()!=null?relpdto[j].getCodAnu():reedto[i].getCodAnu();
												// récupère l'ECTS acquis
												BigDecimal ectsAcquis = elementPedagogiqueService.getCreditAcquisElp(e.getCod_ind(), elp.getCode(),anneeECTS);
												if(ectsAcquis!=null){
													elp.setEcts(Utils.getEctsToDisplay(ectsAcquis)+"/"+relpdto[j].getNbrCrdElp().toString());
												}else{
													elp.setEcts("0/"+relpdto[j].getNbrCrdElp().toString());
												}
											}*/
											
											// Récupération des crédits ECTS version 5.20.laa
											// Si on a un crédit ECTS de référence et si crédit ECTS pas déjà renseigné via la session de juin.
											if(creditEctsElp!=null && (elp.getEcts()==null || elp.getEcts().equals(""))){
												//Si on a un crédit acquis 
												if(relpdto[j].getNbrCrdElp()!= null && relpdto[j].getNbrCrdElp().toString()!=null && !relpdto[j].getNbrCrdElp().toString().equals("")){
													elp.setEcts(Utils.getEctsToDisplay(relpdto[j].getNbrCrdElp())+"/"+creditEctsElp);
												}else{
													elp.setEcts("0/"+creditEctsElp);
												}
											}

											elp.setRes1(result);
										} else {
											//2em session  : septembre
											//l'elp est dans un état de delibération compris dans la liste des témoins paramétrés.
											elpEtatDelibS2OK=true;

											if (relpdto[j].getNotElp() != null && !relpdto[j].getNotElp().equals("null")) {
												elp.setNote2(relpdto[j].getNotElp().toString());
												if(relpdto[j].getNotPntJurElp()!= null && !relpdto[j].getNotPntJurElp().equals(new BigDecimal(0))){
													elp.setNote2(elp.getNote2()+"(+"+relpdto[j].getNotPntJurElp()+")");
												}
											}
											if ((elp.getNote2() == null || (elp.getNote2() != null && elp.getNote2().equals(""))) && result != null && result.equals("DEF")) {
												elp.setNote2("DEF");
											}

											//Gestion du barème:
											if(relpdto[j].getBarNotElp()!= null){
												elp.setBareme2(relpdto[j].getBarNotElp());
											}

											//ajout du rang
											if(relpdto[j].getNbrRngEtuElp() != null && !relpdto[j].getNbrRngEtuElp().equals("")){
												elp.setRang(relpdto[j].getNbrRngEtuElp()+"/"+relpdto[j].getNbrRngEtuElpTot());
											}
											//on récupère l'année car si getCodAnu()!=null c'est un PRC
											if(relpdto[j].getCodAnu()!=null && !relpdto[j].getCodAnu().equals("")){
												elp.setAnnee(relpdto[j].getCodAnu());
												anneePrc = relpdto[j].getCodAnu();
											}
											
											//Récupération des crédits ECTS avant la version 5.20.laa
											//on recupere les crédits ECTS 
											/*if(relpdto[j].getNbrCrdElp()!= null && relpdto[j].getNbrCrdElp().toString()!=null && !relpdto[j].getNbrCrdElp().toString().equals("")){
												// récupère l'ECTS acquis
												String anneeECTS=relpdto[j].getCodAnu()!=null?relpdto[j].getCodAnu():reedto[i].getCodAnu();
												BigDecimal ectsAcquis = elementPedagogiqueService.getCreditAcquisElp(e.getCod_ind(), elp.getCode(), anneeECTS);
												if(ectsAcquis!=null){
													elp.setEcts(Utils.getEctsToDisplay(ectsAcquis)+"/"+relpdto[j].getNbrCrdElp().toString());
												}else{
													elp.setEcts("0/"+relpdto[j].getNbrCrdElp().toString());
												}
											}*/
											
											// Récupération des crédits ECTS version 5.20.laa
											// Si on a un crédit ECTS de référence
											if(creditEctsElp!=null){
												//Si on a un crédit acquis 
												if(relpdto[j].getNbrCrdElp()!= null && relpdto[j].getNbrCrdElp().toString()!=null && !relpdto[j].getNbrCrdElp().toString().equals("")){
													elp.setEcts(Utils.getEctsToDisplay(relpdto[j].getNbrCrdElp())+"/"+creditEctsElp);
												}else{
													elp.setEcts("0/"+creditEctsElp);
												}
											}
											
											elp.setRes2(result);
										}



										//CAS DE NON OBTENTION PAR CORRESPONDANCE.
										if(relpdto[j].getLcc() == null) {

											//ajout de la signification du résultat dans la map
											if (result != null && !result.equals("") && !e.getSignificationResultats().containsKey(result)) {
												e.getSignificationResultats().put(result, relpdto[j].getTypResultat().getLibTre());
											}

										}
									}
								}
								//On affiche la correspondance meme si l'état de délibération n'est pas compris dans la liste des témoins paramétrés.
								if(relpdto[j].getLcc() != null) {
									//les notes ont été obtenues par correspondance a session 1.
									elp.setNote1("COR");
									//ajout de la signification du résultat dans la map
									if ( !e.getSignificationResultats().containsKey("COR")) {
										e.getSignificationResultats().put("COR",applicationContext.getMessage("notesView.signification.type.correspondance", null, Locale.getDefault()));
									}
								}

							}
						}

						//Si il y a un PRC
						if(anneePrc!=null){
							//On doit vérifier que la PRC est valide
							int anneeObtPrc=Integer.parseInt(anneePrc);
							//Récupération de la durée de conservation de  l'élément conservable
							int durConElp = 0;
							if( reedto[i].getElp().getDurConElp()!=null){
								durConElp =reedto[i].getElp().getDurConElp();
								//On test si la conservation est encore valide
								if((anneeObtPrc + durConElp) < anneeResultat){
									//Si ce n'est pas le cas on n'affiche pas les résulats ni l'année.
									elp.setAnnee("");
									elp.setNote1("");
									elp.setBareme1(0);
									elp.setRes1("");
									elp.setNote2("");
									elp.setBareme2(0);
									elp.setRes2("");
									elp.setEcts("");
									elp.setEtatDelib("");
								}
							}

						}

						//ajout de l'élément dans la liste
						if (liste1.size() == 0 || sourceExtractionApogee) {
							liste1.add(elp);
						} else {
							//ajout de l'élément dans la liste par ordre alphabétique
							int rang = 0;
							boolean insere = false;
							while (rang < liste1.size() && !insere) {

								if (liste1.get(rang).getCode().compareTo(elp.getCode()) > 0) {
									liste1.add(rang, elp);
									insere = true;
								}

								if (!insere) {
									rang++;
								}
							}
							if (!insere) {
								liste1.add(elp);
							}
						}
					}

					//les epreuves de l'élément (si il y en a )
					EpreuveElpDTO2[] epelpdto = reedto[i].getEpreuvesElp();

					if (epelpdto != null && epelpdto.length > 0) {

						for (int j = 0; j < epelpdto.length; j++ ) {
							EpreuveElpDTO2 epreuve = epelpdto[j];
							boolean EprNotee = false;  //vrai si l'épreuve est notée
							boolean EprResult = false;  //vrai si l'épreuve a un résultat
							boolean confAffResultatsEpreuve = configController.isAffResultatsEpreuves(); //le paramètre d'affichage des resultats aux épreuves
							ElementPedagogique elp2 = new ElementPedagogique();
							elp2.setLibelle(epreuve.getEpreuve().getLibEpr());
							elp2.setCode(epreuve.getEpreuve().getCodEpr());
							elp2.setLevel(elp.getLevel() + 1);

							//Modif 20/02/2012 pour les WS HttpInvoker
							//elp2.setAnnee("epreuve");
							elp2.setAnnee("");
							elp2.setEpreuve(true);

							elp2.setCodElpSup(elp.getCode());
							elp2.setNote1("");
							elp2.setBareme1(0);
							elp2.setRes1("");
							elp2.setNote2("");
							elp2.setBareme2(0);
							elp2.setRes2("");
							ResultatEprDTO[] repdto = epreuve.getResultatEpr();
							//29/01/10
							//On récupère le témoin TemCtlValCadEpr de l'épreuve
							String TemCtlValCadEpr = epreuve.getEpreuve().getTemCtlValCadEpr();

							if (repdto != null && repdto.length > 0) {
								for (int k = 0; k < repdto.length; k++ ) {
									int codsession = new Integer(repdto[k].getSession().getCodSes());
									//09/01/13
									//On recupere la note si :
									//  On a reseigné une liste de type épreuve à afficher et le type de l'épreuve en fait partie
									//  OU SI :
									//      le témoin d'avc fait partie de la liste des témoins paramétrés 
									//      OU si le témoin d'avc de  l'elp pere fait partie de la liste des témoins paramétrés 
									//      OU si le témoin TemCtlValCadEpr est égal au parametre TemoinCtlValCadEpr de monDossierWeb.xml.
									boolean recuperationNote = false;

									List<String> lTypesEpreuveAffichageNote = configController.getTypesEpreuveAffichageNote();
									if(lTypesEpreuveAffichageNote != null && !lTypesEpreuveAffichageNote.isEmpty()){
										//On a renseigné une liste de type épreuve à afficher
										if(lTypesEpreuveAffichageNote.contains(epreuve.getEpreuve().getTypEpreuve().getCodTep())){
											recuperationNote = true;
										}
									}
									if(!recuperationNote){
										//Si on est dans le cas d'une extraction Apogée
										if(sourceExtractionApogee){
											recuperationNote = true;
										}else{
											//On n'a pas renseigné de liste de type épreuve à afficher ou celui ci n'était pas dans la liste
											if (codsession < 2) {
												if((repdto[k].getEtatDelib()!=null && temoinEtatDelib.contains(repdto[k].getEtatDelib().getCodEtaAvc())) || elpEtatDelibS1OK || TemCtlValCadEpr.equals(configController.getTemoinCtlValCadEpr()))
													recuperationNote = true;
											}else{
												if((repdto[k].getEtatDelib()!=null && temoinEtatDelib.contains(repdto[k].getEtatDelib().getCodEtaAvc())) || elpEtatDelibS2OK || TemCtlValCadEpr.equals(configController.getTemoinCtlValCadEpr()))
													recuperationNote = true;
											}
										}
									}
									//test si on recupere la note ou pas
									if(recuperationNote){
										if (codsession < 2) {
											//1er session  : juin
											if (repdto[k].getNotEpr() != null) {
												elp2.setNote1(repdto[k].getNotEpr().replaceAll(",", "."));

												//Gestion du barème:
												if(repdto[k].getBarNotEpr() != null){
													elp2.setBareme1(repdto[k].getBarNotEpr());
												}
											}
											if (elp2.getNote1() != null && !elp2.getNote1().equals("")) {
												EprNotee = true;
											}

											//le resultat à l'épreuve
											if(confAffResultatsEpreuve && repdto[k].getTypResultat()!=null && StringUtils.hasText(repdto[k].getTypResultat().getCodTre())){
												EprResult = true;
												elp2.setRes1(repdto[k].getTypResultat().getCodTre());
											}


										} else {
											//2er session  : septembre
											if (repdto[k].getNotEpr() != null) {
												elp2.setNote2(repdto[k].getNotEpr().replaceAll(",", "."));

												//Gestion du barème:
												if(repdto[k].getBarNotEpr() != null){
													elp2.setBareme2(repdto[k].getBarNotEpr());
												}
											}
											if (elp2.getNote2() != null && !elp2.getNote2().equals("")) {
												EprNotee = true;
											}

											//le resultat à l'épreuve
											if(confAffResultatsEpreuve && repdto[k].getTypResultat()!=null && StringUtils.hasText(repdto[k].getTypResultat().getCodTre())){
												EprResult = true;
												elp2.setRes2(repdto[k].getTypResultat().getCodTre());
											}
										}
									}
								}
							}
							//ajout de l'épreuve dans la liste en tant qu'élément si elle a une note ou un résultat (si on veut afficher les résultats)
							if (EprNotee || (confAffResultatsEpreuve && EprResult)) {
								liste1.add(elp2);
							}
						}
					}
				}
			}
			//ajout des éléments dans la liste de l'étudiant en commençant par la ou les racine
			int niveauRacine = 1;
			if (liste1.size() > 0) {
				int i = 0;
				while (i < liste1.size()) {
					ElementPedagogique el = liste1.get(i);
					if(sourceExtractionApogee){
						e.getElementsPedagogiques().add(el);
					}else{
						if (el.getCodElpSup() == null || el.getCodElpSup().equals("")) {
							//on a une racine:
							if (!el.getAnnee().equals("FICM")) {
								e.getElementsPedagogiques().add(el);
							}

							insererElmtPedagoFilsDansListe(el, liste1, e, niveauRacine);
						}
					}
					i++;
				}
			}


			//suppression des épreuve seules et quand elles ont les mêmes notes que l'element pere:
			if (!sourceExtractionApogee && e.getElementsPedagogiques().size() > 0) {
				int i = 1;
				boolean suppr = false;
				while (i < e.getElementsPedagogiques().size()) {
					suppr = false;
					ElementPedagogique elp = e.getElementsPedagogiques().get(i);
					if (elp.isEpreuve()) {
						ElementPedagogique elp0 = e.getElementsPedagogiques().get(i - 1);
						if (i < (e.getElementsPedagogiques().size() - 1)) {
							ElementPedagogique elp1 = e.getElementsPedagogiques().get(i + 1);
							if (!elp0.isEpreuve() && !elp1.isEpreuve()) {
								if (elp0.getNote1().equals(elp.getNote1()) && elp0.getNote2().equals(elp.getNote2())) {
									//on supprime l'element i
									e.getElementsPedagogiques().remove(i);
									suppr = true;
								}
							}
						} else {
							if (!elp0.isEpreuve() && elp0.getNote1().equals(elp.getNote1()) && elp0.getNote2().equals(elp.getNote2())) {
								//on supprime l'element i
								e.getElementsPedagogiques().remove(i);
								suppr = true;
							}
						}
					}
					if (!suppr) {
						i++;
					}
				}
			}



			//Gestion des temoins fictif si temoinFictif est renseigné dans monDossierWeb.xml
			if(configController.getTemoinFictif()!=null && !configController.getTemoinFictif().equals("")){
				if (e.getElementsPedagogiques().size() > 0) {
					List<Integer> listeRangAsupprimer=new LinkedList<Integer>();
					int rang = 0;
					//on note les rangs des éléments à supprimer
					for (ElementPedagogique el : e.getElementsPedagogiques()) {
						if(el.getTemFictif()!= null && !el.getTemFictif().equals("") && !el.getTemFictif().equals(configController.getTemoinFictif())){
							//on supprime l'élément de la liste
							listeRangAsupprimer.add(rang);
						}
						rang++;
					}
					//on supprime les éléments de la liste
					int NbElementSupprimes = 0;
					for(Integer rg:listeRangAsupprimer){
						e.getElementsPedagogiques().remove(rg - NbElementSupprimes);
						NbElementSupprimes++;
					}
				}
			}

			//Gestion de la descendance des semestres si temNotesEtuSem est renseigné et à true dans monDossierWeb.xml
			if(configController.isTemNotesEtuSem()){
				if (e.getElementsPedagogiques().size() > 0) {
					List<Integer> listeRangAsupprimer=new LinkedList<Integer>();
					int rang = 0;

					int curSemLevel = 0;
					boolean supDesc = false;

					//on note les rangs des éléments à supprimer
					for (ElementPedagogique el : e.getElementsPedagogiques()) {
						if(el.getTemSemestre()!= null && !el.getTemSemestre().equals("") && el.getTemSemestre().equals("O")) {
							curSemLevel = new Integer(el.getLevel());
							supDesc = el.getEtatDelib()!= null && !el.getEtatDelib().equals("") && !el.getEtatDelib().equals("T");
						} else if(el.getLevel() <= curSemLevel) {
							supDesc = false;
						}

						if(supDesc && el.getLevel() > curSemLevel){
							//on supprime l'élément de la liste
							listeRangAsupprimer.add(rang);
						}
						rang++;
					}
					//on supprime les éléments de la liste
					int NbElementSupprimes = 0;
					for(Integer rg:listeRangAsupprimer){
						e.getElementsPedagogiques().remove(rg - NbElementSupprimes);
						NbElementSupprimes++;
					}
				}
			}

			//ajout de l'étape sélectionnée en début de liste:
			ElementPedagogique ep = new ElementPedagogique();
			ep.setAnnee(et.getAnnee());
			ep.setCode(et.getCode());
			ep.setLevel(1);
			ep.setLibelle(et.getLibelle());
			e.setDeliberationTerminee(et.isDeliberationTerminee());
			if (et.getResultats().size() > 0) {
				if (et.getResultats().get(0).getNote() != null){
					ep.setNote1(et.getResultats().get(0).getNote().toString());
					ep.setBareme1(et.getResultats().get(0).getBareme());
				}
				if (et.getResultats().get(0).getAdmission() != null)
					ep.setRes1(et.getResultats().get(0).getAdmission());

			}
			if (et.getResultats().size() > 1) {
				if (et.getResultats().get(1).getNote() != null){
					ep.setNote2(et.getResultats().get(1).getNote().toString());
					ep.setBareme2(et.getResultats().get(1).getBareme());
				}
				if (et.getResultats().get(1).getAdmission() != null)
					ep.setRes2(et.getResultats().get(1).getAdmission());
			}
			e.getElementsPedagogiques().add(0, ep);

		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			if (ex.getNature().equals("remoteerror")){
				LOG.error("Probleme avec le WS lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}else{
				LOG.info("Probleme avec le WS lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}
		}catch (Exception ex) {
			LOG.error("Probleme lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),ex);
		}
	}



	/**
	 * 
	 * @param elp
	 * @return vrai si l'elp en paramètre a des résulats
	 */
	private boolean elpAvecResultats(ContratPedagogiqueResultatElpEprDTO5 elp) {
		return elp!=null && elp.getResultatsElp()!=null && (elp.getResultatsElp().length > 0);
	}

	/**
	 * ajoute les éléments dans la liste d'éléments de l'étudiant en corrigeant les levels (rangs).
	 * @param elp
	 * @param liste1
	 * @param e
	 * @param niveauDuPere 
	 */
	protected void insererElmtPedagoFilsDansListe(ElementPedagogique elp, List<ElementPedagogique> liste1, Etudiant e, int niveauDuPere) {
		for (ElementPedagogique el : liste1) {
			if (el.getCodElpSup() != null && !el.getCodElpSup().equals("")) {
				if (el.getCodElpSup().equals(elp.getCode()) && !el.getCode().equals(elp.getCode())) {
					//on affiche pas les FICM :
					if (!el.getAnnee().equals("FICM")) {
						el.setLevel(niveauDuPere + 1);
						e.getElementsPedagogiques().add(el);
					}
					//On test si on est pas sur une epreuve pour eviter les boucle infini dans le cas ou codEpr=CodElpPere
					if(!el.getAnnee().equals("epreuve"))
						insererElmtPedagoFilsDansListe(el, liste1, e, niveauDuPere + 1);
				}
			}
		}
	}





	/**
	 * va chercher et renseigne les informations concernant les notes
	 * et résultats des éléments de l'etape choisie
	 * de l'étudiant placé en paramètre via le WS de l'Amue.
	 */
	public void recupererDetailNotesEtResultats(Etudiant e,Etape et, boolean forceSourceApogee){
		try {

			if(monProxyPedagogique==null)
				monProxyPedagogique = (PedagogiqueMetierServiceInterface) WSUtils.getService(WSUtils.PEDAGOGIQUE_SERVICE_NAME, PropertyUtils.getApoWsUsername(),PropertyUtils.getApoWsPassword());

			e.getElementsPedagogiques().clear();

			String temoin = configController.getTemoinNotesEtudiant();
			if(temoin == null || temoin.equals("")){
				temoin="T";
			}

			String temoinEtatIae = configController.getTemoinEtatIaeNotesEtudiant();
			if(temoinEtatIae == null || temoinEtatIae.equals("")){
				temoinEtatIae="E";
			}

			String sourceResultat = PropertyUtils.getSourceResultats();
			if(forceSourceApogee || sourceResultat == null || sourceResultat.equals("")){
				sourceResultat="Apogee";
			}

			//Si on doit se baser sur l'extraction Apogée
			if(utilisationExtractionApogee(et.getAnnee().substring(0, 4),sourceResultat)){
				//On se base sur l'extraction apogée
				sourceResultat="Apogee-extraction";
				temoin=null;
			}else{
				//On va chercher les résultats directement dans Apogée
				sourceResultat="Apogee";
			}

			String anneeParam=et.getAnnee().substring(0, 4);
			int annee = Integer.parseInt(anneeParam);

			//07/09/10
			if(sourceResultat.compareTo("Apogee-extraction")==0){
				//07/09/10
				//on prend le témoin pour Apogee-extraction
				ContratPedagogiqueResultatElpEprDTO5[] cpdto = monProxyPedagogique.recupererContratPedagogiqueResultatElpEpr_v6(e.getCod_etu(), anneeParam, et.getCode(), et.getVersion(), sourceResultat, temoin, "toutes", "tous",temoinEtatIae);
				//29/01/10
				//on est dans le cas d'une extraction apogée
				setNotesElpEpr(e, et, cpdto,"AET",annee,true);
			}else{
				//29/01/10
				//On récupère pour tout les états de délibération et on fera le trie après
				ContratPedagogiqueResultatElpEprDTO5[] cpdto = monProxyPedagogique.recupererContratPedagogiqueResultatElpEpr_v6(e.getCod_etu(), anneeParam, et.getCode(), et.getVersion(), sourceResultat, "AET", "toutes", "tous",temoinEtatIae);
				setNotesElpEpr(e, et, cpdto,temoin,annee,false);
			}



		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			if (ex.getNature().equals("remoteerror")){
				LOG.error(ex.getLastErrorMsg()+" Probleme avec le WS lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}else{
				LOG.info(ex.getLastErrorMsg()+" Probleme avec le WS lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),ex);
			}
		} catch (AxisFault axf) {
			axf.printStackTrace();
			//LOG.info("Probleme lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),axf);
		} catch (Exception ex) {
			LOG.error("Probleme lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),ex);
		}
	}

	/**
	 * va chercher et renseigne les notes de
	 * l'étudiant via le WS de l'Amue.
	 */
	public void recupererDetailNotesEtResultatsEnseignant(Etudiant e,Etape et, boolean forceSourceApogee){
		try {

			if(monProxyPedagogique==null){
				monProxyPedagogique = (PedagogiqueMetierServiceInterface) WSUtils.getService(WSUtils.PEDAGOGIQUE_SERVICE_NAME, PropertyUtils.getApoWsUsername(),PropertyUtils.getApoWsPassword());
			}
			e.getElementsPedagogiques().clear();

			String temoin = configController.getTemoinNotesEnseignant();
			if(temoin == null || temoin.equals("")){
				temoin="AET";
			}

			String temoinEtatIae = configController.getTemoinEtatIaeNotesEnseignant();
			if(temoinEtatIae == null || temoinEtatIae.equals("")){
				temoinEtatIae="E";
			}


			String sourceResultat = PropertyUtils.getSourceResultats();
			if(forceSourceApogee || sourceResultat == null || sourceResultat.equals("")){
				sourceResultat="Apogee";
			}


			//Si on doit se baser sur l'extraction Apogée
			if(utilisationExtractionApogee(et.getAnnee().substring(0, 4),sourceResultat)){
				//On se base sur l'extraction apogée
				sourceResultat="Apogee-extraction";
				temoin=null;
			}else{
				//On va chercher les résultats directement dans Apogée
				sourceResultat="Apogee";
			}


			String anneeParam=et.getAnnee().substring(0, 4);
			int annee = Integer.parseInt(anneeParam);

			// 07/12/11 récupération du fonctionnement identique à la récupéraition des notes pour les étudiants.
			if(sourceResultat.compareTo("Apogee-extraction")==0){
				ContratPedagogiqueResultatElpEprDTO5[] cpdto = monProxyPedagogique.recupererContratPedagogiqueResultatElpEpr_v6(e.getCod_etu(), anneeParam , et.getCode(), et.getVersion(), sourceResultat, temoin, "toutes", "tous",temoinEtatIae);
				setNotesElpEpr(e, et, cpdto,"AET",annee,true);
			}else{
				ContratPedagogiqueResultatElpEprDTO5[] cpdto = monProxyPedagogique.recupererContratPedagogiqueResultatElpEpr_v6(e.getCod_etu(), anneeParam , et.getCode(), et.getVersion(), sourceResultat, "AET", "toutes", "tous",temoinEtatIae);
				setNotesElpEpr(e, et, cpdto,temoin,annee,false);
			}


		} catch (WebBaseException ex) {
			//Si on est dans un cas d'erreur non expliqué
			if (ex.getNature().equals("remoteerror")){
				LOG.error("Probleme avec le WS lors de la recherche des notes et résultats a une étape pour etudiant dont codind est : " + e.getCod_ind(),ex);
			}else{
				LOG.info(ex.getLastErrorMsg()+" pour etudiant dont codind est : " + e.getCod_ind() + " recupererDetailNotesEtResultatsEnseignant("+et.getAnnee()+ ","+et.getCode()+"/"+et.getVersion()+")");
			}
		} catch (AxisFault axf) {
			axf.printStackTrace();
			//LOG.info("Probleme lors de la recherche des notes et résultats a une étape pour etudiant dont codetu est : " + e.getCod_etu(),axf);
		}catch (Exception ex) {
			LOG.error("Probleme lors de la recherche des notes et résultats a une étape pour etudiant dont codind est : " + e.getCod_ind(),ex);
		}

	}


	public boolean utilisationExtractionApogee(String annee) {

		int anneeEnCours = new Integer(etudiantController.getAnneeUnivEnCours(GenericUI.getCurrent()));
		int anneeDemandee = new Integer(annee);

		//Si l'extraction Apogée couvre l'année demandée
		if (anneeDemandee>=(anneeEnCours - (configController.getNotesNombreAnneesExtractionApogee() - 1 ))) {
			//On peut se baser sur l'extraction apogée
			return true;
		} 

		return false;
	}

	public boolean utilisationExtractionApogee(String annee, String sourceResultat) {
		// Si sourceResultat = apogee-extraction :
		if(sourceResultat.compareTo("Apogee-extraction")==0){
			return utilisationExtractionApogee(annee); 
		}
		return false;
	}

	public void renseigneNotesEtResultats(Etudiant e) {
		//On regarde si on a pas déjà les infos dans le cache:
		String rang = getRangNotesEtResultatsEnCache(true,e);

		if(rang == null){
			recupererNotesEtResultats(e);
			//AJOUT DES INFOS recupérées dans le cache. true car on est en vue Etudiant
			ajouterCacheResultatVdiVet(true,e);
		}else{
			//on récupére les infos du cache grace au rang :
			recupererCacheResultatVdiVet(new Integer(rang),e);
		}
	}

	public void renseigneNotesEtResultatsVueEnseignant(Etudiant e) {
		//On regarde si on a pas déjà les infos dans le cache:
		String rang = getRangNotesEtResultatsEnCache(false,e);
		if(rang == null){
			recupererNotesEtResultatsEnseignant(e);
			//AJOUT DES INFOS recupérées dans le cache. true car on est en vue Etudiant
			ajouterCacheResultatVdiVet(false,e);
		}else{
			//on récupére les infos du cache grace au rang :
			recupererCacheResultatVdiVet(new Integer(rang),e);
		}
	}
	public void renseigneDetailInscription(Etape etape) {
		//Récupération de la source des résultats
		String sourceResultat = PropertyUtils.getSourceResultats();
		if(sourceResultat == null || sourceResultat.equals("")){
			sourceResultat="Apogee";
		}

		//Si on devait se baser sur l'extraction apogée pour récupérer les notes à l'étape
		if(utilisationExtractionApogee(etape.getAnnee().substring(0, 4),sourceResultat)){
			LOG.info("Méthode de récupération de l'IP basée sur Apogée au lieu de l'extraction");
			//On regarde si on a pas déjà les infos dans le cache:
			String rang = getRangDetailInscriptionEnCache(etape,GenericUI.getCurrent().getEtudiant());

			if(rang == null){
				recupererDetailNotesEtResultats(GenericUI.getCurrent().getEtudiant(),etape,true);
				//AJOUT DES INFOS recupérées dans le cache.
				ajouterCacheDetailInscription(etape,GenericUI.getCurrent().getEtudiant());
			}else{
				//on récupére les infos du cache grace au rang :
				recupererCacheDetailInscription(new Integer(rang),GenericUI.getCurrent().getEtudiant());
			}

		}else{
			LOG.info("Méthode de récupération de l'IP identique à la récupération des notes");
			//Méthode de récupération de l'IP commune aux notes
			renseigneDetailNotesEtResultats(etape);
		}
	}

	public void renseigneDetailInscriptionEnseignant(Etape etape) {
		//Récupération de la source des résultats
		String sourceResultat = PropertyUtils.getSourceResultats();
		if(sourceResultat == null || sourceResultat.equals("")){
			sourceResultat="Apogee";
		}

		//Si on devait se baser sur l'extraction apogée pour récupérer les notes à l'étape
		if(utilisationExtractionApogee(etape.getAnnee().substring(0, 4),sourceResultat)){
			LOG.info("Méthode de récupération de l'IP basée sur Apogée au lieu de l'extraction");
			//On regarde si on a pas déjà les infos dans le cache:
			String rang = getRangDetailInscriptionEnCache(etape,GenericUI.getCurrent().getEtudiant());

			if(rang == null){
				recupererDetailNotesEtResultatsEnseignant(GenericUI.getCurrent().getEtudiant(),etape,true);
				//AJOUT DES INFOS recupérées dans le cache.
				ajouterCacheDetailInscription(etape,GenericUI.getCurrent().getEtudiant());
			}else{
				//on récupére les infos du cache grace au rang :
				recupererCacheDetailInscription(new Integer(rang),GenericUI.getCurrent().getEtudiant());
			}
		}else{
			LOG.info("Méthode de récupération de l'IP identique à la récupération des notes");
			//Méthode de récupération de l'IP commune aux notes
			renseigneDetailNotesEtResultatsEnseignant(etape);
		}

	}

	public void renseigneDetailNotesEtResultats(Etape etape) {
		//On regarde si on a pas déjà les infos dans le cache:
		String rang = getRangDetailNotesEtResultatsEnCache(etape,true,GenericUI.getCurrent().getEtudiant());

		if(rang == null){
			recupererDetailNotesEtResultats(GenericUI.getCurrent().getEtudiant(),etape,false);
			//AJOUT DES INFOS recupérées dans le cache. true car on est en vue Etudiant
			ajouterCacheDetailNotesEtResultats(etape,true,GenericUI.getCurrent().getEtudiant());
		}else{
			//on récupére les infos du cache grace au rang :
			recupererCacheDetailNotesEtResultats(new Integer(rang),GenericUI.getCurrent().getEtudiant());
		}
	}

	public void renseigneDetailNotesEtResultatsEnseignant(Etape etape) {
		//On regarde si on a pas déjà les infos dans le cache:
		String rang = getRangDetailNotesEtResultatsEnCache(etape,false,GenericUI.getCurrent().getEtudiant());
		if(rang == null){
			recupererDetailNotesEtResultatsEnseignant(GenericUI.getCurrent().getEtudiant(),etape,false);
			//AJOUT DES INFOS recupérées dans le cache. false car on est en vue Enseignant
			ajouterCacheDetailNotesEtResultats(etape,false,GenericUI.getCurrent().getEtudiant());
		}else{
			//on récupére les infos du cache grace au rang :
			recupererCacheDetailNotesEtResultats(new Integer(rang),GenericUI.getCurrent().getEtudiant());
		}
	}


	/* 
	 * @param etape
	 * @param vueEtudiant
	 * @return  le rang dans la liste des IP en cache pour la vueEtudiant
	 */
	private String getRangDetailInscriptionEnCache(Etape etape, Etudiant e){
		int rang=0;
		boolean enCache=false;

		//on parcourt les IP pour voir si on a ce qu'on cherche:
		for(CacheIP cip : e.getCacheResultats().getIp()){
			//Si on n'a pas déjà trouvé les infos dans le cache
			if(!enCache){
				//test si on a les infos:
				if(cip.getEtape().getAnnee().equals(etape.getAnnee())
						&& cip.getEtape().getCode().equals(etape.getCode())
						&& cip.getEtape().getVersion().equals(etape.getVersion())){
					enCache=true;
				}else{
					//on a pas trouvé, on incrémente le rang pour se placer sur le rang suivant
					rang++;
				}
			}
		}

		//si on a pas les infos en cache:
		if(!enCache){
			return null;
		}

		return ""+rang;

	}

	/* 
	 * @param etape
	 * @param vueEtudiant
	 * @return  le rang dans la liste des Notes et Résultat (aux elp et epr) en cache pour la vueEtudiant
	 */
	private String getRangDetailNotesEtResultatsEnCache(Etape etape, boolean vueEtudiant, Etudiant e){
		int rang=0;
		boolean enCache=false;

		//on parcourt le résultatElpEpr pour voir si on a ce qu'on cherche:
		for(CacheResultatsElpEpr cree : e.getCacheResultats().getResultElpEpr()){
			if(!enCache){
				//si on a déjà les infos:
				if(cree.getEtape().getAnnee().equals(etape.getAnnee())
						&& cree.getEtape().getCode().equals(etape.getCode())
						&& cree.getEtape().getVersion().equals(etape.getVersion())
						&& cree.isVueEtudiant() == vueEtudiant){
					enCache=true;
				}else{
					//on a pas trouvé, on incrémente le rang pour se placer sur le rang suivant
					rang++;
				}
			}
		}

		//si on a pas les infos en cache:
		if(!enCache){
			return null;
		}

		return ""+rang;

	}

	/**
	 * 
	 * @param vueEtudiant
	 * @return le rang dans la liste des Notes et Résultat (aux diplomes et étapes) en cache pour la vueEtudiant
	 */
	private String getRangNotesEtResultatsEnCache(boolean vueEtudiant, Etudiant e){
		int rang=0;
		boolean enCache=false;

		//on parcourt le résultatVdiVet pour voir si on a ce qu'on cherche:
		if(e.getCacheResultats()!=null && e.getCacheResultats().getResultVdiVet()!=null){
			for(CacheResultatsVdiVet crvv : e.getCacheResultats().getResultVdiVet()){
				if(!enCache){
					//si on a déjà les infos:
					if(crvv.isVueEtudiant() == vueEtudiant){
						enCache=true;
					}else{
						//on a pas trouvé, on incrémente le rang pour se placer sur le rang suivant
						rang++;
					}
				}
			}
		}
		//si on a pas les infos en cache:
		if(!enCache){
			return null;
		}

		return ""+rang;

	}


	/**
	 * On complète les infos du cache pour les Résultats aux diplomes et étapes.
	 * @param vueEtudiant
	 */
	public void ajouterCacheResultatVdiVet(boolean vueEtudiant, Etudiant e){
		CacheResultatsVdiVet crvv = new CacheResultatsVdiVet();
		crvv.setVueEtudiant(vueEtudiant);
		crvv.setDiplomes(new LinkedList<Diplome>(e.getDiplomes()));
		crvv.setEtapes(new LinkedList<Etape>(e.getEtapes()));
		e.getCacheResultats().getResultVdiVet().add(crvv);
	}

	/**
	 * On complète les infos du cache pour les Résultats aux elp et epr.
	 * @param vueEtudiant
	 */
	public void ajouterCacheDetailNotesEtResultats(Etape etape, boolean vueEtudiant, Etudiant e){
		CacheResultatsElpEpr cree = new CacheResultatsElpEpr();
		cree.setVueEtudiant(vueEtudiant);
		cree.setEtape(etape);
		if(e.getElementsPedagogiques()!=null && e.getElementsPedagogiques().size()>0){
			cree.setElementsPedagogiques(new LinkedList<ElementPedagogique>(e.getElementsPedagogiques()));
		}
		e.getCacheResultats().getResultElpEpr().add(cree);
	}


	/**
	 * On complète les infos du cache pour l'IP.
	 */
	public void ajouterCacheDetailInscription(Etape etape,Etudiant e){
		CacheIP cip = new CacheIP();
		cip.setEtape(etape);
		if(e.getElementsPedagogiques()!=null && e.getElementsPedagogiques().size()>0){
			cip.setElementsPedagogiques(new LinkedList<ElementPedagogique>(e.getElementsPedagogiques()));
		}
		e.getCacheResultats().getIp().add(cip);
	}


	/**
	 * récupère les résultat aux diplomes et etapes dans le cache (en s'indexant sur le rang)
	 * @param rang
	 */
	private void recupererCacheResultatVdiVet(int rang, Etudiant e){
		//1-on vide les listes existantes
		if(e.getDiplomes()!=null){
			e.getDiplomes().clear();
		}
		if(e.getEtapes()!=null){
			e.getEtapes().clear();
		}
		//2-on récupère les infos du cache.
		if(e.getCacheResultats().getResultVdiVet().get(rang).getDiplomes()!=null){
			e.setDiplomes(new LinkedList<Diplome>(e.getCacheResultats().getResultVdiVet().get(rang).getDiplomes()));
		}
		if(e.getCacheResultats().getResultVdiVet().get(rang).getEtapes()!=null){
			e.setEtapes(new LinkedList<Etape>(e.getCacheResultats().getResultVdiVet().get(rang).getEtapes()));
		}

	}

	/**
	 * récupère les résultat aux Elp et Epr dans le cache (en s'indexant sur le rang)
	 * @param rang
	 */
	private void recupererCacheDetailNotesEtResultats(int rang, Etudiant e){
		//1-on vide la liste existante
		if(e.getElementsPedagogiques()!=null){
			e.getElementsPedagogiques().clear();
		}

		//2-on récupère les infos du cache.
		if(e.getCacheResultats().getResultElpEpr().get(rang).getElementsPedagogiques()!=null){
			e.setElementsPedagogiques(new LinkedList<ElementPedagogique>(e.getCacheResultats().getResultElpEpr().get(rang).getElementsPedagogiques()));
		}

	}

	/**
	 * récupère les infos sur l'IP dans le cache (en s'indexant sur le rang)
	 * @param rang
	 */
	private void recupererCacheDetailInscription(int rang, Etudiant e){
		//1-on vide la liste existante
		if(e.getElementsPedagogiques()!=null){
			e.getElementsPedagogiques().clear();
		}

		//2-on récupère les infos du cache.
		if(e.getCacheResultats().getIp().get(rang).getElementsPedagogiques()!=null){
			e.setElementsPedagogiques(new LinkedList<ElementPedagogique>(e.getCacheResultats().getIp().get(rang).getElementsPedagogiques()));
		}

	}


	public void changerVueNotesEtResultats() {
		if(GenericUI.getCurrent().isVueEnseignantNotesEtResultats()){
			GenericUI.getCurrent().setVueEnseignantNotesEtResultats(false);
		}else{
			GenericUI.getCurrent().setVueEnseignantNotesEtResultats(true);
		}
	}


	public boolean isAfficherRangElpEpr(){
		List<ElementPedagogique> lelp = GenericUI.getCurrent().getEtudiant().getElementsPedagogiques();
		if(lelp != null && lelp.size()>0){
			List<String> codesAutorises = configController.getListeCodesEtapeAffichageRang();
			if(codesAutorises!=null && codesAutorises.contains(lelp.get(0).getCode())){
				return true;
			}
		}
		return false;
	}



}
