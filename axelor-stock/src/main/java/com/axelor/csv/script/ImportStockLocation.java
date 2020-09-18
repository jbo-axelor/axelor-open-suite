package com.axelor.csv.script;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.service.AddressServiceImpl;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.util.List;
import java.util.Map;

public class ImportStockLocation {

  @Transactional
  public Object importStockLocation(Object bean, Map<String, Object> values) {
    assert bean instanceof StockLocation;
    try {
      AddressRepository addressRepository = Beans.get(AddressRepository.class);
      StockLocationRepository stockLocationRepository = Beans.get(StockLocationRepository.class);
      StockLocation stockLocation = (StockLocation) bean;
      String addressString = (String) values.get("Adresse");
      addressString = addressString.trim();
      String[] addressTab = addressString.split(" ");
      String addressl6 =
          addressTab[addressTab.length - 2] + " " + addressTab[addressTab.length - 1];
      String addressl4 = "";
      for (int i = 0; i < addressTab.length - 2; i++) {
        if (i == addressTab.length - 3) {
          addressl4 += addressTab[i];
        } else {
          addressl4 += addressTab[i] + " ";
        }
      }
      if (addressString != null && !addressString.isEmpty()) {
        List<Address> addresses =
            addressRepository
                .all()
                .filter("self.addressL4 = :address")
                .bind("address", addressl4)
                .fetch();
        if (addresses.size() < 1) {
          Address address =
              Beans.get(AddressServiceImpl.class)
                  .createAddress("", "", addressl4, "", addressl6, null);
          stockLocation.setAddress(address);
          Beans.get(AddressRepository.class).save(address);
        } else {
          stockLocation.setAddress(addresses.get(0));
        }
      }

      String stockParentName = (String) values.get("Emplacement_parent");
      if (stockParentName != null && !stockParentName.isEmpty()) {
        List<StockLocation> parent =
            stockLocationRepository
                .all()
                .filter("self.name = :parent")
                .bind("parent", stockParentName)
                .fetch();
        if (parent.size() > 0) {
          stockLocation.setParentStockLocation(parent.get(0));
        }
        //				else
        //				{
        //					StockLocation stockLocation2 =
        // Beans.get(StockLocationServiceImpl.class).createStockLocation(false, stockParentName, 0,
        // null, stockLocation.getCompany(), null, stockLocation.getPartner());
        //					stockLocation.setParentStockLocation(stockLocation2);
        //					stockLocationRepository.save(stockLocation2);
        //				}
      }
      return stockLocation;
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }
}
