/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.repo.TimetableRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderInvoiceServiceImpl implements PurchaseOrderInvoiceService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private InvoiceService invoiceService;

  @Inject private InvoiceRepository invoiceRepo;

  @Inject protected TimetableRepository timetableRepo;

  @Inject protected AppSupplychainService appSupplychainService;

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(PurchaseOrder purchaseOrder) throws AxelorException {

    Invoice invoice = this.createInvoice(purchaseOrder);
    invoice = invoiceRepo.save(invoice);
    invoiceService.setDraftSequence(invoice);
    invoice.setAddressStr(Beans.get(AddressService.class).computeAddressStr(invoice.getAddress()));
    return invoice;
  }

  @Override
  public Invoice createInvoice(PurchaseOrder purchaseOrder) throws AxelorException {

    InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(purchaseOrder);

    Invoice invoice = invoiceGenerator.generate();

    List<InvoiceLine> invoiceLineList =
        this.createInvoiceLines(invoice, purchaseOrder.getPurchaseOrderLineList());

    invoiceGenerator.populate(invoice, invoiceLineList);

    invoice.setPurchaseOrder(purchaseOrder);
    return invoice;
  }

  @Override
  public InvoiceGenerator createInvoiceGenerator(PurchaseOrder purchaseOrder)
      throws AxelorException {
    return createInvoiceGenerator(purchaseOrder, false);
  }

  @Override
  public InvoiceGenerator createInvoiceGenerator(PurchaseOrder purchaseOrder, boolean isRefund)
      throws AxelorException {

    if (purchaseOrder.getCurrency() == null) {
      throw new AxelorException(
          purchaseOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.PO_INVOICE_1),
          purchaseOrder.getPurchaseOrderSeq());
    }

    return new InvoiceGeneratorSupplyChain(purchaseOrder, isRefund) {
      @Override
      public Invoice generate() throws AxelorException {
        return super.createInvoiceHeader();
      }
    };
  }

  @Override
  public List<InvoiceLine> createInvoiceLines(
      Invoice invoice, List<PurchaseOrderLine> purchaseOrderLineList) throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {
      processPurchaseOrderLine(invoice, invoiceLineList, purchaseOrderLine);
    }
    return invoiceLineList;
  }

  @Override
  public void processPurchaseOrderLine(
      Invoice invoice, List<InvoiceLine> invoiceLineList, PurchaseOrderLine purchaseOrderLine)
      throws AxelorException {
    invoiceLineList.addAll(this.createInvoiceLine(invoice, purchaseOrderLine));
    purchaseOrderLine.setInvoiced(true);
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(Invoice invoice, PurchaseOrderLine purchaseOrderLine)
      throws AxelorException {

    Product product = purchaseOrderLine.getProduct();

    InvoiceLineGeneratorSupplyChain invoiceLineGenerator =
        new InvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            purchaseOrderLine.getProductName(),
            purchaseOrderLine.getDescription(),
            purchaseOrderLine.getQty(),
            purchaseOrderLine.getUnit(),
            purchaseOrderLine.getSequence(),
            false,
            null,
            purchaseOrderLine,
            null) {
          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();

            List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  @Override
  public BigDecimal getInvoicedAmount(PurchaseOrder purchaseOrder) {
    return this.getInvoicedAmount(purchaseOrder, null, true);
  }

  /**
   * Return the remaining amount to invoice for the purchaseOrder in parameter
   *
   * <p>In the case of invoice ventilation or cancellation, the invoice status isn't modify in
   * database but it will be integrated in calculation For ventilation, the invoice should be
   * integrated in calculation For cancellation, the invoice shouldn't be integrated in calculation
   *
   * <p>To know if the invoice should be or not integrated in calculation
   *
   * @param purchaseOrder
   * @param currentInvoiceId
   * @param excludeCurrentInvoice
   * @return
   */
  @Override
  public BigDecimal getInvoicedAmount(
      PurchaseOrder purchaseOrder, Long currentInvoiceId, boolean excludeCurrentInvoice) {

    BigDecimal invoicedAmount = BigDecimal.ZERO;

    BigDecimal purchaseAmount =
        this.getAmountVentilated(
            purchaseOrder,
            currentInvoiceId,
            excludeCurrentInvoice,
            InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE);
    BigDecimal refundAmount =
        this.getAmountVentilated(
            purchaseOrder,
            currentInvoiceId,
            excludeCurrentInvoice,
            InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND);

    if (purchaseAmount != null) {
      invoicedAmount = invoicedAmount.add(purchaseAmount);
    }
    if (refundAmount != null) {
      invoicedAmount = invoicedAmount.subtract(refundAmount);
    }

    if (!purchaseOrder.getCurrency().equals(purchaseOrder.getCompany().getCurrency())
        && purchaseOrder.getCompanyExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
      BigDecimal rate =
          invoicedAmount.divide(purchaseOrder.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
      invoicedAmount = purchaseOrder.getExTaxTotal().multiply(rate);
    }

    log.debug(
        "Compute the invoiced amount ({}) of the purchase order : {}",
        invoicedAmount,
        purchaseOrder.getPurchaseOrderSeq());

    return invoicedAmount;
  }

  private BigDecimal getAmountVentilated(
      PurchaseOrder purchaseOrder,
      Long currentInvoiceId,
      boolean excludeCurrentInvoice,
      int invoiceOperationTypeSelect) {

    String query =
        "SELECT SUM(self.companyExTaxTotal)"
            + " FROM InvoiceLine as self"
            + " WHERE ((self.purchaseOrderLine.purchaseOrder.id = :purchaseOrderId AND self.invoice.purchaseOrder IS NULL)"
            + " OR self.invoice.purchaseOrder.id = :purchaseOrderId )"
            + " AND self.invoice.operationTypeSelect = :invoiceOperationTypeSelect"
            + " AND self.invoice.statusSelect = :statusVentilated";

    if (currentInvoiceId != null) {
      if (excludeCurrentInvoice) {
        query += " AND self.invoice.id <> :invoiceId";
      } else {
        query +=
            " OR (self.invoice.id = :invoiceId AND self.invoice.operationTypeSelect = :invoiceOperationTypeSelect) ";
      }
    }

    Query q = JPA.em().createQuery(query, BigDecimal.class);

    q.setParameter("purchaseOrderId", purchaseOrder.getId());
    q.setParameter("statusVentilated", InvoiceRepository.STATUS_VENTILATED);
    q.setParameter("invoiceOperationTypeSelect", invoiceOperationTypeSelect);
    if (currentInvoiceId != null) {
      q.setParameter("invoiceId", currentInvoiceId);
    }

    BigDecimal invoicedAmount = (BigDecimal) q.getSingleResult();

    if (invoicedAmount != null) {
      return invoicedAmount;
    } else {
      return BigDecimal.ZERO;
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected Invoice generateInvoiceFromTimetableForPurchaseOrder(
      PurchaseOrder purchaseOrder, List<Long> timetableIdList) throws AxelorException {
    if (ObjectUtils.isEmpty(timetableIdList)) {
      throw new AxelorException(
          purchaseOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SO_INVOICE_NO_TIMETABLES_SELECTED));
    }
    BigDecimal percentSum = BigDecimal.ZERO;
    List<Timetable> timetableList = new ArrayList<>();
    for (Long timetableId : timetableIdList) {
      Timetable timetable = timetableRepo.find(timetableId);
      timetableList.add(timetable);
      percentSum = percentSum.add(timetable.getPercentage());
    }
    Invoice invoice = generateInvoiceFromLines(purchaseOrder, percentSum);

    for (Timetable timetable : timetableList) {
      timetable.setInvoice(invoice);
      timetable.setInvoiced(true);
      timetableRepo.save(timetable);
    }
    return invoice;
  }

  public Invoice generateInvoiceFromLines(PurchaseOrder purchaseOrder, BigDecimal percentSum)
      throws AxelorException {

    if (percentSum.equals(BigDecimal.ZERO)) {
      throw new AxelorException(
          purchaseOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SO_INVOICE_NO_LINES_SELECTED));
    }

    Map<Long, BigDecimal> qtyToInvoiceMap = new HashMap<>();

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      BigDecimal realQty =
          purchaseOrderLine
              .getQty()
              .multiply(percentSum)
              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
      qtyToInvoiceMap.put(purchaseOrderLine.getId(), realQty);

      if (qtyToInvoiceMap.get(purchaseOrderLine.getId()).compareTo(purchaseOrderLine.getQty())
          > 0) {
        throw new AxelorException(
            purchaseOrderLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.PO_INVOICE_QTY_MAX));
      }
    }
    return this.generateInvoice(
        purchaseOrder, purchaseOrder.getPurchaseOrderLineList(), qtyToInvoiceMap);
  }

  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(
      PurchaseOrder purchaseOrder,
      List<PurchaseOrderLine> purchaseOrderLinesSelected,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    Invoice invoice =
        this.createInvoice(purchaseOrder, purchaseOrderLinesSelected, qtyToInvoiceMap);
    invoiceRepo.save(invoice);

    Beans.get(PurchaseOrderRepository.class).save(fillPurchaseOrder(purchaseOrder, invoice));

    return invoice;
  }

  public Invoice createInvoice(
      PurchaseOrder purchaseOrder,
      List<PurchaseOrderLine> purchaseOrderLineList,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(purchaseOrder);

    Invoice invoice = invoiceGenerator.generate();

    invoiceGenerator.populate(
        invoice, this.createInvoiceLines(invoice, purchaseOrderLineList, qtyToInvoiceMap));

    return invoice;
  }

  public List<InvoiceLine> createInvoiceLines(
      Invoice invoice,
      List<PurchaseOrderLine> purchaseOrderLineList,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<>();
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

      if (qtyToInvoiceMap.containsKey(purchaseOrderLine.getId())) {
        List<InvoiceLine> invoiceLines =
            this.createInvoiceLine(
                invoice, purchaseOrderLine, qtyToInvoiceMap.get(purchaseOrderLine.getId()));
        invoiceLineList.addAll(invoiceLines);
        purchaseOrderLine.setInvoiced(true);
      }
    }

    return invoiceLineList;
  }

  public List<InvoiceLine> createInvoiceLine(
      Invoice invoice, PurchaseOrderLine purchaseOrderLine, BigDecimal qtyToInvoice)
      throws AxelorException {

    Product product = purchaseOrderLine.getProduct();

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            purchaseOrderLine.getProductName(),
            purchaseOrderLine.getDescription(),
            qtyToInvoice,
            purchaseOrderLine.getUnit(),
            purchaseOrderLine.getSequence(),
            false,
            null,
            purchaseOrderLine,
            null) {

          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();

            List<InvoiceLine> invoiceLines = new ArrayList<>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  public PurchaseOrder fillPurchaseOrder(PurchaseOrder purchaseOrder, Invoice invoice) {

    purchaseOrder.setOrderDate(appSupplychainService.getTodayDate(purchaseOrder.getCompany()));

    return purchaseOrder;
  }
}
