package com.axelor.csv.script;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.helpdesk.db.Ticket;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
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
      UserRepository userRepository = Beans.get(UserRepository.class);
      PartnerRepository partnerRepository = Beans.get(PartnerRepository.class);
      Ticket ticket = (Ticket) bean;
      String client = (String) values.get("Client");
      String contact = (String) values.get("Contact");
      String responsibleUser = (String) values.get("Utilisateur_responsable");
      if (responsibleUser != null) {
        if (!responsibleUser.isEmpty() && responsibleUser != "") {
          List<User> users =
              userRepository
                  .all()
                  .filter("self.name = :responsibleUser")
                  .bind("responsibleUser", responsibleUser)
                  .fetch();
          if (users.size() > 0) {
            ticket.setResponsibleUser(users.get(0));
          }
        }
      }

      if (client != null) {
        if (!client.isEmpty() && client != "") {
          List<Partner> clients =
              partnerRepository
                  .all()
                  .filter("self.simpleFullName = :client")
                  .bind("client", client)
                  .fetch();
          if (clients.size() > 0) {
            ticket.setCustomer(clients.get(0));
          }
        }
      }

      if (contact != null) {
        if (!contact.isEmpty() && contact != "") {
          List<Partner> contacts =
              partnerRepository
                  .all()
                  .filter("self.simpleFullName = :contact")
                  .bind("contact", contact)
                  .fetch();
          if (contacts.size() > 0) {
            ticket.setContactPartner(contacts.get(0));
          }
        }
      }

      String startDateString = (String) values.get("Date_de_debut");
      String endDateString = (String) values.get("Date_de_fin");
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
      if (startDateString != null) {
        if (!startDateString.isEmpty() && startDateString != "") {
          LocalDateTime startDate = LocalDateTime.parse(startDateString, dtf);
          ticket.setStartDateT(startDate);
        }
      }
      if (endDateString != null) {
        if (!endDateString.isEmpty() && endDateString != "") {
          LocalDateTime endDate = LocalDateTime.parse(endDateString, dtf);
          ticket.setEndDateT(endDate);
        }
      }
      System.out.println(ticket.toString());
      return ticket;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
