package com.axelor.csv.script;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.AddressServiceImpl;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;

public class ImportStockLocation {

	private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Transactional
	public Object importStockLocation(Object bean, Map<String, Object> values) {
		assert bean instanceof StockLocation;
		try {
			StockLocation stockLocation = (StockLocation) bean;
			if (stockLocation == null) {
				stockLocation = new StockLocation();
			}

			String companyName = (String) values.get("Societe");
			setCompany(stockLocation, companyName);

			String name = (String) values.get("Nom");
			setName(stockLocation, name);

			String addressString = (String) values.get("Adresse");
			setAddress(stockLocation, addressString);

			return stockLocation;
		} catch (Exception e) {

			LOG.debug("Error while importing Stock Location {}", e);
		}

		return null;
	}

	private void setAddress(StockLocation stockLocation, String addressString) {
		AddressRepository addressRepository = Beans.get(AddressRepository.class);
		addressString = addressString.trim();
		String[] addressTab = addressString.split(" ");
		String addressl6 = addressTab[addressTab.length - 2] + " " + addressTab[addressTab.length - 1];
		String addressl4 = "";
		for (int i = 0; i < addressTab.length - 2; i++) {
			if (i == addressTab.length - 3) {
				addressl4 += addressTab[i];
			} else {
				addressl4 += addressTab[i] + " ";
			}
		}
		if (StringUtils.isNotBlank(addressString)) {
			Address address = addressRepository.all().filter("self.addressL4 = :address")
					.bind("address", addressl4).fetchOne();
			if (address == null) {
				Address address2 = Beans.get(AddressServiceImpl.class).createAddress("", "", addressl4, "", addressl6,
						null);
				stockLocation.setAddress(address2);
				Beans.get(AddressRepository.class).save(address);
			} else {
				stockLocation.setAddress(address);
			}
		}

	}

	private void setName(StockLocation stockLocation, String name) throws AxelorException {
		if (StringUtils.isNotBlank(name)) {
			stockLocation.setName(name);
		} else {
			throw new AxelorException(stockLocation.getClass(), TraceBackRepository.CATEGORY_MISSING_FIELD,
					I18n.get(IExceptionMessage.STOCK_LOCATION_NAME_MISSING_ERROR), "No name set for stock location");
		}

	}

	private void setCompany(StockLocation stockLocation, String companyName) throws AxelorException {
		CompanyRepository companyRepository = Beans.get(CompanyRepository.class);
		if (StringUtils.isNotBlank(companyName)) {
			Company company = companyRepository.all().filter("self.name = :name").bind("name", companyName).fetchOne();
			if (company != null) {
				stockLocation.setCompany(company);
			} else {
				throw new AxelorException(stockLocation.getClass(), TraceBackRepository.CATEGORY_MISSING_FIELD,
						I18n.get(IExceptionMessage.COMPANY_MISSING_ERROR), "No company name set for stock location");
			}
		} else {
			throw new AxelorException(stockLocation.getClass(), TraceBackRepository.CATEGORY_MISSING_FIELD,
					I18n.get(IExceptionMessage.COMPANY_MISSING_ERROR), "No company name set for stock location");
		}
	}
}
