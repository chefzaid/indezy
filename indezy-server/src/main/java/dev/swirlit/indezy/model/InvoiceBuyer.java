package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/** The client as the invoice stated it when issued: later changes to the client leave it as it was. */
@Embeddable
@Getter
@Setter
public class InvoiceBuyer {

    @Column(name = "buyer_name", length = 255)
    private String name;

    @Column(name = "buyer_address", length = 400)
    private String address;

    @Column(name = "buyer_siren", length = 9)
    private String siren;

    @Column(name = "buyer_vat_number", length = 20)
    private String vatNumber;
}
