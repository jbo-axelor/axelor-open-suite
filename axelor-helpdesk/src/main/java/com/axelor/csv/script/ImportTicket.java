package com.axelor.csv.script;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.helpdesk.db.Ticket;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ImportTicket {

	@Transactional
	public Object importTicket(Object bean, Map<String, Object> values) {
		assert bean instanceof Ticket;
		try {
			Ticket ticket = (Ticket) bean;
			
			String client = (String) values.get("Client");
			String contact = (String) values.get("Contact");
			String responsibleUser = (String) values.get("Utilisateur_responsable");
			setResponsibleUser(ticket, responsibleUser);
			setClient(ticket, client);
			setContact(ticket, contact);
			
			String startDateString = (String) values.get("Date_de_debut");
			String endDateString = (String) values.get("Date_de_fin");
			setStartDate(ticket, startDateString);
			setEndDate(ticket, endDateString);
	
			ticket.setSubject((String) values.get("Sujet"));
			
			String status = (String) values.get("Statut");
			setStatus(ticket, status);
			
			String progress = (String) values.get("Progression");
			setProgress(ticket, progress);

			String duree = (String) values.get("Duree");
			setDuration(ticket, duree);
			
			String priority = (String) values.get("Priorite");
			setPriority(ticket, priority);
			
			return ticket;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void setResponsibleUser(Ticket ticket, String responsibleUser){
		UserRepository userRepository = Beans.get(UserRepository.class);
		if (!StringUtils.isBlank(responsibleUser)) {

			List<User> users = userRepository.all().filter("self.name = :responsibleUser")
					.bind("responsibleUser", responsibleUser).fetch();
			if (users.size() > 0) {
				ticket.setResponsibleUser(users.get(0));
			}	
		}
	}
	
	private void setClient(Ticket ticket, String client){
		PartnerRepository partnerRepository = Beans.get(PartnerRepository.class);
		if (!StringUtils.isBlank(client)) {
			List<Partner> clients = partnerRepository.all().filter("self.simpleFullName = :client")
					.bind("client", client).fetch();
			if (clients.size() > 0) {
				ticket.setCustomer(clients.get(0));	
			}
		}
	}
	
	private void setContact(Ticket ticket, String contact)
	{
		PartnerRepository partnerRepository = Beans.get(PartnerRepository.class);
		if (!StringUtils.isBlank(contact)) {
			
			List<Partner> contacts = partnerRepository.all().filter("self.simpleFullName = :contact")
					.bind("contact", contact).fetch();
			if (contacts.size() > 0) {
				ticket.setContactPartner(contacts.get(0));
			}
		}
	}
	
	private void setStartDate(Ticket ticket, String startDateString)
	{
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
		
		if (!StringUtils.isBlank(startDateString)) {
			LocalDateTime startDate = LocalDateTime.parse(startDateString, dtf);
			ticket.setStartDateT(startDate);
		}
	}
	
	private void setEndDate(Ticket ticket, String endDateString)
	{
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
		
		if (!StringUtils.isBlank(endDateString)) {
			LocalDateTime endDate = LocalDateTime.parse(endDateString, dtf);
			ticket.setEndDateT(endDate);
		}
	}
	
	private void setStatus(Ticket ticket, String status)
	{
		if (StringUtils.isBlank(status))
			ticket.setStatusSelect(0);
		else {
			if (status.equals("Résolu")) {
				ticket.setStatusSelect(2);
			}
			if (status.equals("Nouveau")) {
				ticket.setStatusSelect(0);
			}
			if (status.equals("En cours")) {
				ticket.setStatusSelect(1);
			}
			if (status.equals("Annulé")) {
				ticket.setStatusSelect(3);
			}
		}
	}
	
	private void setProgress(Ticket ticket, String progress)
	{
		
		if (StringUtils.isBlank(progress))
		{
			ticket.setProgressSelect(0);
		}
		else
		{
			ticket.setProgressSelect(Integer.valueOf(progress.split(" ")[0]));
		}
	}
	
	private void setDuration(Ticket ticket, String duree)
	{
		if (StringUtils.isBlank(duree))
		{
			ticket.setDuration(new Long(0));
		}
		else
		{
			ticket.setDuration(Long.valueOf(duree)*3600);
		}
	}
	
	private void setPriority(Ticket ticket, String priority)
	{
		if (StringUtils.isBlank(priority))
			ticket.setPrioritySelect(1);
		else {
			if (priority.equals("Faible")) {
				ticket.setPrioritySelect(1);
			}
			if (priority.equals("Moyen")) {
				ticket.setPrioritySelect(2);
			}
			if (priority.equals("Élevé")) {
				ticket.setPrioritySelect(3);
			}
			if (priority.equals("Urgent")) {
				ticket.setPrioritySelect(4);
			}
		}
	}
}
