package com.global.api.entities.gpApi;

import com.global.api.builders.AuthorizationBuilder;
import com.global.api.entities.*;
import com.global.api.entities.enums.*;
import com.global.api.entities.exceptions.GatewayException;
import com.global.api.gateways.GpApiConnector;
import com.global.api.paymentMethods.*;
import com.global.api.utils.EmvUtils;
import com.global.api.utils.EnumUtils;
import com.global.api.utils.JsonDoc;
import com.global.api.utils.StringUtils;
import lombok.var;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static com.global.api.entities.enums.TransactionType.Refund;
import static com.global.api.entities.gpApi.GpApiManagementRequestBuilder.getDccId;
import static com.global.api.gateways.GpApiConnector.getDateIfNotNull;
import static com.global.api.gateways.GpApiConnector.getValueIfNotNull;
import static com.global.api.utils.EnumUtils.mapDigitalWalletType;
import static com.global.api.utils.StringUtils.isNullOrEmpty;

public class GpApiAuthorizationRequestBuilder {

    public static GpApiRequest buildRequest(AuthorizationBuilder builder, GpApiConnector gateway) throws GatewayException {
        String merchantUrl = gateway.getMerchantUrl();
        JsonDoc paymentMethod =
                new JsonDoc()
                        .set("entry_mode", getEntryMode(builder, gateway.getGpApiConfig().getChannel())); // [MOTO, ECOM, IN_APP, CHIP, SWIPE, MANUAL, CONTACTLESS_CHIP, CONTACTLESS_SWIPE]

        paymentMethod.set("narrative", !StringUtils.isNullOrEmpty(builder.getDynamicDescriptor()) ? builder.getDynamicDescriptor() : null);

        IPaymentMethod builderPaymentMethod = builder.getPaymentMethod();
        TransactionType builderTransactionType = builder.getTransactionType();
        TransactionModifier builderTransactionModifier = builder.getTransactionModifier();
        Address builderBillingAddress = builder.getBillingAddress();

        if (builderPaymentMethod instanceof CreditCardData && (builderTransactionModifier == TransactionModifier.EncryptedMobile || builderTransactionModifier == TransactionModifier.DecryptedMobile))
        {
            JsonDoc digitalWallet = new JsonDoc();
            CreditCardData creditCardData = (CreditCardData) builderPaymentMethod;

            //Digital Wallet
            if (builderTransactionModifier == TransactionModifier.EncryptedMobile) {
                var payment_token = new JsonDoc();

                switch (creditCardData.getMobileType()) {
                    case CLICK_TO_PAY:
                        payment_token.set("data", creditCardData.getToken());
                        break;

                    default:
                        payment_token = JsonDoc.parse(creditCardData.getToken());
                        break;
                }

                digitalWallet
                        .set("payment_token", payment_token);
            }
            else if (builderTransactionModifier == TransactionModifier.DecryptedMobile)
            {
                DigitalWalletTokenFormat tokenFormat = DigitalWalletTokenFormat.CARD_NUMBER;
                digitalWallet
                        .set("token", creditCardData.getToken())
                        .set("token_format", DigitalWalletTokenFormat.CARD_NUMBER.getValue())
                        .set("expiry_month", creditCardData.getExpMonth() != null ? StringUtils.padLeft(creditCardData.getExpMonth(), 2, '0') : null)
                        .set("expiry_year", creditCardData.getExpYear() != null ? StringUtils.padLeft(creditCardData.getExpYear(), 4, '0').substring(2, 4) : null)
                        .set("cryptogram", creditCardData.getCryptogram())
                        .set("eci", creditCardData.getEci());
            }
            digitalWallet.set("provider", mapDigitalWalletType(Target.GP_API, ((CreditCardData) builderPaymentMethod).getMobileType()));
            paymentMethod.set("digital_wallet", digitalWallet);
        } else {
            // CardData
            if (builderPaymentMethod instanceof ICardData) {
                ICardData cardData = (ICardData) builderPaymentMethod;

                JsonDoc card = new JsonDoc();
                card.set("number", cardData.getNumber());
                card.set("expiry_month", cardData.getExpMonth() != null ? StringUtils.padLeft(cardData.getExpMonth().toString(), 2, '0') : null);
                card.set("expiry_year", cardData.getExpYear() != null ? cardData.getExpYear().toString().substring(2, 4) : null);
                //card.set("track", "");
                card.set("tag", builder.getTagData());
                card.set("cvv", cardData.getCvn());
                card.set("avs_address", builderBillingAddress != null ? builderBillingAddress.getStreetAddress1() : "");
                card.set("avs_postal_code", builderBillingAddress != null ? builderBillingAddress.getPostalCode() : "");
                card.set("authcode", builder.getOfflineAuthCode());
                card.set("brand_reference", builder.getCardBrandTransactionId());

                card.set("chip_condition", builder.getEmvChipCondition()); // [PREV_SUCCESS, PREV_FAILED]

                // Avoid setting transaction types requesting to: POST /payment-methods
                if (!(builderTransactionType == TransactionType.Tokenize || builderTransactionType == TransactionType.Verify)) {
                    card.set("cvv_indicator", !getValueIfNotNull(cardData.getCvnPresenceIndicator()).equals("0") ? getCvvIndicator(cardData.getCvnPresenceIndicator()) : null); // [ILLEGIBLE, NOT_PRESENT, PRESENT]
                    card.set("funding", builderPaymentMethod.getPaymentMethodType() == PaymentMethodType.Debit ? "DEBIT" : "CREDIT"); // [DEBIT, CREDIT]
                }

                boolean hasToken = false;
                if (builderPaymentMethod instanceof ITokenizable) {
                    ITokenizable tokenData = (ITokenizable) builderPaymentMethod;
                    hasToken = (tokenData != null) && !StringUtils.isNullOrEmpty(tokenData.getToken());

                }

                if (!hasToken) {
                    paymentMethod.set("card", card);
                }
                // Brand reference when card was tokenized
                else {
                    JsonDoc brand =
                            new JsonDoc()
                                    .set("brand_reference", builder.getCardBrandTransactionId());
                    if (!brand.getKeys().isEmpty()) {
                        paymentMethod.set("card", brand);
                    }
                }

                if (builderTransactionType == TransactionType.Tokenize) {
                    JsonDoc tokenizationData = new JsonDoc();
                    tokenizationData.set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTokenizationAccountName());
                    tokenizationData.set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? java.util.UUID.randomUUID().toString() : builder.getClientTransactionId());
                    tokenizationData.set("usage_mode", builder.getPaymentMethodUsageMode());
                    tokenizationData.set("card", card);

                    return
                            new GpApiRequest()
                                    .setVerb(GpApiRequest.HttpMethod.Post)
                                    .setEndpoint(merchantUrl + "/payment-methods")
                                    .setRequestBody(tokenizationData.toString());
                }
                else if (builderTransactionType == TransactionType.DccRateLookup) {
                    // tokenized payment method
                    if (builderPaymentMethod instanceof ITokenizable) {
                        String token = ((ITokenizable) builderPaymentMethod).getToken();
                        if (!StringUtils.isNullOrEmpty(token)) {
                            paymentMethod.set("id", token);
                        }
                    }

                    var requestData =
                            new JsonDoc()
                                    .set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTransactionProcessingAccountName())
                                    .set("channel", gateway.getGpApiConfig().getChannel())
                                    .set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? java.util.UUID.randomUUID().toString() : builder.getClientTransactionId())
                                    .set("amount", StringUtils.toNumeric(builder.getAmount()))
                                    .set("currency", builder.getCurrency())
                                    .set("country", gateway.getGpApiConfig().getCountry())
                                    .set("payment_method", paymentMethod);

                    return
                            new GpApiRequest()
                                    .setVerb(GpApiRequest.HttpMethod.Post)
                                    .setEndpoint(merchantUrl + "/currency-conversions")
                                    .setRequestBody(requestData.toString());
                }
                else if (builderTransactionType == TransactionType.Verify) {
                    if (builder.isRequestMultiUseToken() && StringUtils.isNullOrEmpty(((ITokenizable) builderPaymentMethod).getToken())) {
                        JsonDoc tokenizationData = new JsonDoc();
                        tokenizationData.set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTokenizationAccountName());
                        tokenizationData.set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? java.util.UUID.randomUUID().toString() : builder.getClientTransactionId());
                        tokenizationData.set("usage_mode", builder.getPaymentMethodUsageMode());
                        tokenizationData.set("fingerprint_mode", builder.getCustomerData() != null ? builder.getCustomerData().getDeviceFingerPrint() : null);
                        tokenizationData.set("card", card);

                        return
                                new GpApiRequest()
                                        .setVerb(GpApiRequest.HttpMethod.Post)
                                        .setEndpoint(merchantUrl + "/payment-methods")
                                        .setRequestBody(tokenizationData.toString());

                    }
                    else {
                        JsonDoc verificationData =
                                new JsonDoc()
                                        .set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTransactionProcessingAccountName())
                                        .set("channel", gateway.getGpApiConfig().getChannel())
                                        .set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? java.util.UUID.randomUUID().toString() : builder.getClientTransactionId())
                                        .set("currency", builder.getCurrency())
                                        .set("country", gateway.getGpApiConfig().getCountry())
                                        .set("payment_method", paymentMethod);

                        if (builderPaymentMethod instanceof ITokenizable && !StringUtils.isNullOrEmpty(((ITokenizable) builderPaymentMethod).getToken())) {
                            verificationData.remove("payment_method");
                            verificationData.set("payment_method",
                                    new JsonDoc()
                                            .set("entry_mode", getEntryMode(builder, gateway.getGpApiConfig().getChannel()))
                                            .set("id", ((ITokenizable) builderPaymentMethod).getToken())
                                            .set("fingerprint_mode", builder.getCustomerData() != null ? builder.getCustomerData().getDeviceFingerPrint() : null));
                        }

                        return
                                new GpApiRequest()
                                        .setVerb(GpApiRequest.HttpMethod.Post)
                                        .setEndpoint(merchantUrl + "/verifications")
                                        .setRequestBody(verificationData.toString());
                    }
                }
            }

            // TrackData
            else if (builderPaymentMethod instanceof ITrackData) {
                ITrackData track = (ITrackData) builderPaymentMethod;

                JsonDoc card =
                        new JsonDoc()
                                .set("track", track.getValue())
                                .set("tag", builder.getTagData())
                                .set("avs_address", builderBillingAddress != null ? builderBillingAddress.getStreetAddress1() : "")
                                .set("avs_postal_code", builderBillingAddress != null ? builderBillingAddress.getPostalCode() : "")
                                .set("authcode", builder.getOfflineAuthCode());

                if (builderTransactionType == TransactionType.Verify) {
                    paymentMethod.set("card", card);

                    JsonDoc verificationData = new JsonDoc()
                            .set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTransactionProcessingAccountName())
                            .set("channel", gateway.getGpApiConfig().getChannel())
                            .set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? UUID.randomUUID().toString() : builder.getClientTransactionId())
                            .set("currency", builder.getCurrency())
                            .set("country", gateway.getGpApiConfig().getCountry())
                            .set("payment_method", paymentMethod)
                            .set("fingerprint_mode", builder.getCustomerData() != null ? builder.getCustomerData().getDeviceFingerPrint() : null);

                    return
                            new GpApiRequest()
                                    .setVerb(GpApiRequest.HttpMethod.Post)
                                    .setEndpoint(merchantUrl + "/verifications")
                                    .setRequestBody(verificationData.toString());
                }

                if (builderTransactionType == TransactionType.Sale || builderTransactionType == TransactionType.Refund) {
                    if (StringUtils.isNullOrEmpty(track.getValue())) {
                        card.set("number", track.getPan());
                        card.set("expiry_month", track.getExpiry().substring(2, 4));
                        card.set("expiry_year", track.getExpiry().substring(0, 2));
                    }
                    if (StringUtils.isNullOrEmpty(builder.getTagData())) {
                        card.set("chip_condition", getChipCondition(builder.getEmvChipCondition())); // [PREV_SUCCESS, PREV_FAILED]
                    }
                }

                if (builderTransactionType == TransactionType.Sale) {
                    card.set("funding", builderPaymentMethod.getPaymentMethodType() == PaymentMethodType.Debit ? "DEBIT" : "CREDIT"); // [DEBIT, CREDIT]
                }

                paymentMethod.set("card", card);
            }

            // Tokenized Payment Method
            if (builderPaymentMethod instanceof ITokenizable) {
                String token = ((ITokenizable) builderPaymentMethod).getToken();
                if (!StringUtils.isNullOrEmpty(token)) {
                    paymentMethod.set("id", token);
                }
            }
        }
        // Payment Method Storage Mode
        if (builder.isRequestMultiUseToken()) {
            paymentMethod.set("storage_mode", "ON_SUCCESS");
        }

        // Pin Block
        if (builderPaymentMethod instanceof IPinProtected) {
            if (paymentMethod.get("card") != null) {
                paymentMethod.get("card").set("pin_block", ((IPinProtected) builderPaymentMethod).getPinBlock());
            }
        }

        // Authentication
        if (builderPaymentMethod instanceof CreditCardData) {
            CreditCardData creditCardData = (CreditCardData) builderPaymentMethod;
            paymentMethod.set("name", creditCardData.getCardHolderName());

            paymentMethod.set("fingerprint_mode", builder.getCustomerData() != null ? builder.getCustomerData().getDeviceFingerPrint() : null);

            ThreeDSecure secureEcom = creditCardData.getThreeDSecure();
            if (secureEcom != null) {
                ArrayList<HashMap<String, Object>> three_ds = new ArrayList<>();
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put("exempt_status", secureEcom.getExemptStatus() != null ? secureEcom.getExemptStatus().getValue() : null);
                three_ds.add(hashMap);

                JsonDoc authentication =
                        new JsonDoc()
                                .set("id", secureEcom.getServerTransactionId())
                                .set("three_ds",  three_ds);

                paymentMethod.set("authentication", authentication);
            }
        }

        if(builderPaymentMethod instanceof EBT) {
            EBT ebt = (EBT) builderPaymentMethod;
            paymentMethod.set("name", ebt.getCardHolderName());
        }

        if (builderPaymentMethod instanceof eCheck) {
            eCheck check = (eCheck) builderPaymentMethod;
            paymentMethod.set("name", check.getCheckHolderName());

            JsonDoc bankTransfer =
                    new JsonDoc()
                            .set("account_number", check.getAccountNumber())
                            .set("account_type", (check.getAccountType() != null) ? EnumUtils.getMapping(Target.GP_API, check.getAccountType()) : null)
                            .set("check_reference", check.getCheckReference())
                            .set("sec_code", check.getSecCode())
                            .set("narrative", check.getMerchantNotes());

            JsonDoc bank =
                    new JsonDoc()
                            .set("code", check.getRoutingNumber())
                            .set("name", check.getBankName());

            if(check.getBankAddress() != null) {
                Address checkBankAddress = check.getBankAddress();
                JsonDoc address =
                        new JsonDoc()
                                .set("line_1", checkBankAddress.getStreetAddress1())
                                .set("line_2", checkBankAddress.getStreetAddress2())
                                .set("line_3", checkBankAddress.getStreetAddress3())
                                .set("city", checkBankAddress.getCity())
                                .set("postal_code", checkBankAddress.getPostalCode())
                                .set("state", checkBankAddress.getState())
                                .set("country", checkBankAddress.getCountryCode());

                bank.set("address", address);
            }

            bankTransfer.set("bank", bank);

            paymentMethod.set("bank_transfer", bankTransfer);

        }

        if (builderPaymentMethod instanceof AlternativePaymentMethod) {
            var alternatepaymentMethod = (AlternativePaymentMethod) builderPaymentMethod;

            paymentMethod.set("name", alternatepaymentMethod.getAccountHolderName());

            var apm = new JsonDoc()
                    .set("provider", alternatepaymentMethod.getAlternativePaymentMethodType().getValue())
                    .set("address_override_mode", alternatepaymentMethod.getAddressOverrideMode());

            paymentMethod.set("apm", apm);
        }

        if (builderPaymentMethod instanceof BNPL) {
            BNPL bnpl = (BNPL) builder.getPaymentMethod();

            var bnplType =
                    new JsonDoc()
                            .set("provider", EnumUtils.getMapping(Target.GP_API, bnpl.BNPLType));

            paymentMethod
                    .set("name", builder.getCustomerData() != null ? builder.getCustomerData().getFirstName() + " " + builder.getCustomerData().getLastName() : null)
                    .set("bnpl", bnplType);
        }

        // Encryption
        if (builderPaymentMethod instanceof IEncryptable) {
            IEncryptable encryptable = (IEncryptable) builderPaymentMethod;
            EncryptionData encryptionData = encryptable.getEncryptionData();

            if (encryptionData != null) {
                JsonDoc encryption =
                        new JsonDoc()
                                .set("version", encryptionData.getVersion());

                if (!StringUtils.isNullOrEmpty(encryptionData.getKtb())) {
                    encryption.set("method", "KTB");
                    encryption.set("info", encryptionData.getKtb());
                }
                else if (!StringUtils.isNullOrEmpty(encryptionData.getKsn())) {
                    encryption.set("method", "KSN");
                    encryption.set("info", encryptionData.getKsn());
                }

                if (encryption.has("info")) {
                    paymentMethod.set("encryption", encryption);
                }
            }
        }

        if (builderTransactionType == TransactionType.Create && builder.getPayLinkData() instanceof PayLinkData) {
            var payLinkData = builder.getPayLinkData();

            var requestData =
                    new JsonDoc()
                            .set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTransactionProcessingAccountName())
                            .set("type", payLinkData.getType().toString())
                            .set("usage_mode", payLinkData.getUsageMode() != null ? payLinkData.getUsageMode().getValue() : null)
                            .set("usage_limit",  payLinkData.getUsageLimit() != null ? payLinkData.getUsageLimit() : null)
                            .set("reference", builder.getClientTransactionId() != null ? builder.getClientTransactionId() : java.util.UUID.randomUUID().toString())
                            .set("name", payLinkData.getName() != null ? payLinkData.getName() : null)
                            .set("description", builder.getDescription() != null ? builder.getDescription() : null)
                            .set("shippable", payLinkData.isShippable() == Boolean.TRUE ? "YES" : "NO")
                            .set("shipping_amount", StringUtils.toNumeric(payLinkData.getShippingAmount()))
                            .set("expiration_date", payLinkData.getExpirationDate() != null ? getDateIfNotNull(payLinkData.getExpirationDate()) : null)
                            // .set("status", payLinkData.getStatus() != null ? payLinkData.getStatus().toString() : null)
                            .set("status", PayLinkStatus.ACTIVE.toString())
                            .set("images", payLinkData.getImages() != null ? payLinkData.getImages().toString() : null);

            var transactions =
                    new JsonDoc()
                            .set("amount", StringUtils.toNumeric(builder.getAmount()))
                            .set("channel", gateway.getGpApiConfig().getChannel())
                            .set("currency", builder.getCurrency())
                            .set("country", gateway.getGpApiConfig().getCountry())
                            .set("allowed_payment_methods", payLinkData.getAllowedPaymentMethods());

            var notifications =
                    new JsonDoc()
                            .set("cancel_url", payLinkData.getCancelUrl())
                            .set("return_url", payLinkData.getReturnUrl())
                            .set("status_url", payLinkData.getStatusUpdateUrl());

            requestData.set("transactions", transactions);
            requestData.set("notifications", notifications);

            return
                    new GpApiRequest()
                            .setVerb(GpApiRequest.HttpMethod.Post)
                            .setEndpoint(merchantUrl + "/links")
                            .setRequestBody(requestData.toString());

        }

        JsonDoc data = new JsonDoc()
                .set("account_name", gateway.getGpApiConfig().getAccessTokenInfo().getTransactionProcessingAccountName())
                .set("type", builderTransactionType == Refund ? "REFUND" : "SALE") // [SALE, REFUND]
                .set("channel", gateway.getGpApiConfig().getChannel()) // [CP, CNP]
                .set("capture_mode", getCaptureMode(builder)) // [AUTO, LATER, MULTIPLE]
                //.set("remaining_capture_count", "") // Pending Russell
                .set("authorization_mode", builder.isAllowPartialAuth() ? "PARTIAL" : null)
                .set("amount", StringUtils.toNumeric(builder.getAmount()))
                .set("currency", builder.getCurrency())
                .set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? java.util.UUID.randomUUID().toString() : builder.getClientTransactionId())
                .set("description", builder.getDescription())
                //.set("order_reference", builder.getOrderId())
                .set("gratuity_amount", StringUtils.toNumeric(builder.getGratuity()))
                .set("cashback_amount", StringUtils.toNumeric(builder.getCashBackAmount()))
                .set("surcharge_amount", StringUtils.toNumeric(builder.getSurchargeAmount()))
                .set("convenience_amount", StringUtils.toNumeric(builder.getConvenienceAmount()))
                .set("country", gateway.getGpApiConfig().getCountry())
                //.set("language", language)
                .set("ip_address", builder.getCustomerIpAddress())
                //.set("site_reference", "")
                .set("currency_conversion", builder.getDccRateData() != null ? getDccId(builder.getDccRateData()) : null)
                .set("payment_method", paymentMethod)
                .set("risk_assessment", builder.getFraudFilterMode() != null ? mapFraudManagement(builder) : null)
                .set("link", !StringUtils.isNullOrEmpty(builder.getPaymentLinkId()) ?
                        new JsonDoc().set("id", builder.getPaymentLinkId()) : null);

        if (builderPaymentMethod instanceof CreditCardData && (((CreditCardData) builderPaymentMethod).getMobileType() == MobilePaymentMethodType.CLICK_TO_PAY)) {
            data.set("masked", builder.getMaskedDataResponse() ? "YES" : "NO");
        }

        if (builderPaymentMethod instanceof eCheck || builderPaymentMethod instanceof AlternativePaymentMethod || builderPaymentMethod instanceof BNPL) {
            data.set("payer", setPayerInformation(builder));
        }

        // Set Order Reference
        if (!StringUtils.isNullOrEmpty(builder.getOrderId())) {
            JsonDoc order =
                    new JsonDoc()
                            .set("reference", builder.getOrderId());

            data.set("order", order);
        }

        if (builderPaymentMethod instanceof AlternativePaymentMethod || builderPaymentMethod instanceof BNPL) {
            setOrderInformation(builder, data);

            INotificationData payment = null;

            if (builderPaymentMethod instanceof AlternativePaymentMethod) {
                payment = (AlternativePaymentMethod) builderPaymentMethod;
            }

            if (builderPaymentMethod instanceof BNPL) {
                payment = (BNPL) builderPaymentMethod;
            }

            var notifications =
                    new JsonDoc()
                            .set("return_url", payment.getReturnUrl())
                            .set("status_url", payment.getStatusUpdateUrl())
                            .set("cancel_url", payment.getCancelUrl());

            data.set("notifications", notifications);
        }

        // Stored Credential
        if (builder.getStoredCredential() != null) {
            data.set("initiator", EnumUtils.getMapping(Target.GP_API, builder.getStoredCredential().getInitiator()));
            JsonDoc storedCredential =
                    new JsonDoc()
                            .set("model", EnumUtils.getMapping(Target.GP_API, builder.getStoredCredential().getType()))
                            .set("reason", EnumUtils.getMapping(Target.GP_API, builder.getStoredCredential().getReason()))
                            .set("sequence", EnumUtils.getMapping(Target.GP_API, builder.getStoredCredential().getSequence()));
            data.set("stored_credential", storedCredential);
        }

        return
                new GpApiRequest()
                        .setVerb(GpApiRequest.HttpMethod.Post)
                        .setEndpoint(merchantUrl + "/transactions")
                        .setRequestBody(data.toString());
    }

    private static JsonDoc setPayerInformation(AuthorizationBuilder builder) {
        JsonDoc payer = new JsonDoc();
        payer.set("reference", builder.getCustomerId() != null ? builder.getCustomerId() : (builder.getCustomerData() != null ? builder.getCustomerData().getId() : null));

        if(builder.getPaymentMethod() instanceof eCheck) {
            JsonDoc billingAddress = new JsonDoc();

            Address builderBillingAddress = builder.getBillingAddress();

            if(builderBillingAddress != null) {
                billingAddress
                        .set("line_1", builderBillingAddress.getStreetAddress1())
                        .set("line_2", builderBillingAddress.getStreetAddress2())
                        .set("city", builderBillingAddress.getCity())
                        .set("postal_code", builderBillingAddress.getPostalCode())
                        .set("state", builderBillingAddress.getProvince())
                        .set("country", builderBillingAddress.getCountryCode());

                payer.set("billing_address", billingAddress);
            }

            if (builder.getCustomerData() != null) {
                payer.set("name", builder.getCustomerData().getFirstName() + " " + builder.getCustomerData().getLastName());
                payer.set("date_of_birth", builder.getCustomerData().getDateOfBirth());
            }

            payer.set("landline_phone", StringUtils.toNumeric(builder.getCustomerData().getHomePhone()) != null ? StringUtils.toNumeric(builder.getCustomerData().getHomePhone()) : builder.getHomePhone().toString());
            payer.set("mobile_phone", StringUtils.toNumeric(builder.getCustomerData().getMobilePhone()) != null ? StringUtils.toNumeric(builder.getCustomerData().getMobilePhone()) : builder.getMobilePhone().toString());
        } else if (builder.getPaymentMethod() instanceof AlternativePaymentMethod) {

            if (builder.getHomePhone() != null) {
                var homePhone =
                        new JsonDoc()
                                .set("country_code", builder.getHomePhone().getCountryCode())
                                .set("subscriber_number", builder.getHomePhone().getNumber());

                payer.set("home_phone", homePhone);
            }

            if (builder.getWorkPhone() != null) {
                var workPhone =
                        new JsonDoc()
                                .set("country_code", builder.getWorkPhone().getCountryCode())
                                .set("subscriber_number", builder.getWorkPhone().getNumber());

                payer.set("work_phone", workPhone);
            }
        } else if(builder.getPaymentMethod() instanceof BNPL && builder.getCustomerData() != null) {
            payer
                    .set("email", builder.getCustomerData().getEmail())
                    .set("date_of_birth", builder.getCustomerData().getDateOfBirth());

            JsonDoc billing_address = new JsonDoc();

            if (builder.getBillingAddress() != null) {
                billing_address
                        .set("line_1", builder.getBillingAddress().getStreetAddress1())
                        .set("line_2", builder.getBillingAddress().getStreetAddress2())
                        .set("city", builder.getBillingAddress().getCity())
                        .set("postal_code", builder.getBillingAddress().getPostalCode())
                        .set("state", builder.getBillingAddress().getState())
                        .set("country", builder.getBillingAddress().getCountryCode());
            }

            if (builder.getCustomerData() != null) {
                billing_address
                        .set("first_name", builder.getCustomerData().getFirstName() != null ? builder.getCustomerData().getFirstName() : "")
                        .set("last_name", builder.getCustomerData().getLastName() != null ? builder.getCustomerData().getLastName() : "");

                payer.set("billing_address", billing_address);

                if (builder.getCustomerData().getPhone() != null) {
                    JsonDoc homePhone = new JsonDoc();

                    homePhone
                            .set("country_code", builder.getCustomerData().getPhone().getCountryCode())
                            .set("subscriber_number", builder.getCustomerData().getPhone().getNumber());

                    payer.set("contact_phone", homePhone);

                }

                if (builder.getCustomerData().getDocuments() != null) {

                    var documents = new ArrayList<HashMap<String, Object>>();
                    for (var document : builder.getCustomerData().getDocuments()) {
                        var doc = new HashMap<String, Object>();

                        doc.put("type", document.getType().toString());
                        doc.put("reference", document.getReference());
                        doc.put("issuer", document.getIssuer());

                        documents.add(doc);
                    }

                    payer.set("documents", documents);
                }
            }
        }

        return payer;
    }

    public static ArrayList<HashMap<String, Object>> mapFraudManagement(AuthorizationBuilder builder) {
        ArrayList<HashMap<String, Object>> rules = new ArrayList<>();
        if (builder.getFraudRules() != null) {
            for (var fraudRule : builder.getFraudRules().getRules()) {
                HashMap<String, Object> rule = new HashMap<>();
                rule.put("reference", fraudRule.getKey());
                rule.put("mode", fraudRule.getMode().getValue());
                rules.add(rule);
            }
        }

        ArrayList<HashMap<String, Object>> result = new ArrayList<>();
        HashMap<String, Object> item = new HashMap<>();
        item.put("mode", builder.getFraudFilterMode().getValue());

        if (rules.size() > 0) {
            item.put("rules", rules);
        }

        result.add(item);

        return result;
    }

    private static String getEntryMode(AuthorizationBuilder builder, String channel) {
        IPaymentMethod builderPaymentMethod = builder.getPaymentMethod();

        if (channel.equals(Channel.CardPresent.getValue())) {
            if (builderPaymentMethod instanceof ITrackData) {
                ITrackData paymentMethod = (ITrackData) builderPaymentMethod;
                if (!StringUtils.isNullOrEmpty(builder.getTagData())) {
                    if (paymentMethod.getEntryMethod() == EntryMethod.Proximity) {
                        return "CONTACTLESS_CHIP";
                    }
                    var emvData = EmvUtils.parseTagData(builder.getTagData());
                    if (emvData.isContactlessMsd()) {
                        return "CONTACTLESS_SWIPE";
                    }
                    return "CHIP";
                }
                if (paymentMethod.getEntryMethod() == EntryMethod.Swipe) {
                    return "SWIPE";
                }
            }
            if (builderPaymentMethod instanceof ICardData && ((ICardData) builderPaymentMethod).isCardPresent()) {
                return "MANUAL";
            }
            return "SWIPE";
        }
        else {
            if (builderPaymentMethod instanceof ICardData) {
                ICardData paymentMethod = (ICardData) builderPaymentMethod;

                if (paymentMethod.isReaderPresent()) {
                    return "ECOM";
                }
                else {
                    if (paymentMethod.getEntryMethod() != null) {
                        switch (paymentMethod.getEntryMethod()) {
                            case Phone:
                                return "PHONE";
                            case Moto:
                                return "MOTO";
                            case Mail:
                                return "MAIL";
                            default:
                                break;
                        }
                    }
                }

                if (    builder.getTransactionModifier() == TransactionModifier.EncryptedMobile &&
                        builderPaymentMethod instanceof CreditCardData &&
                        ((CreditCardData) builder.getPaymentMethod()).hasInAppPaymentData()
                ) {
                    return "IN_APP";
                }
            }

            return "ECOM";
        }
    }

    private static String getCaptureMode(AuthorizationBuilder builder) {
        if (builder.isMultiCapture()) {
            return "MULTIPLE";
        }
        else if (builder.getTransactionType() == TransactionType.Auth) {
            return "LATER";
        }
        return "AUTO";
    }

    private static JsonDoc setItemDetailsListForBNPL(AuthorizationBuilder builder, JsonDoc order) {
        var items = new ArrayList<HashMap<String, Object>>();
        for (var product : builder.getMiscProductData()) {
            var item = new HashMap<String, Object>();
            Integer qta = product.getQuantity() != null ? product.getQuantity() : 0;
            BigDecimal taxAmount = product.getTaxAmount() != null ? product.getTaxAmount() : new BigDecimal(0);
            BigDecimal unitAmount = product.getUnitPrice() != null ? product.getUnitPrice() : new BigDecimal(0);
            BigDecimal netUnitAmount = product.getNetUnitAmount() != null ? product.getNetUnitAmount() : new BigDecimal(0);
            BigDecimal discountAmount = product.getDiscountAmount() != null ? product.getDiscountAmount() : new BigDecimal(0);

            item.put("reference", product.getProductId() != null ? product.getProductId() : null);
            item.put("label", product.getProductName() != null ? product.getProductName() : null);
            item.put("description", product.getDescription() != null ? product.getDescription() : null);
            item.put("quantity", qta.toString());
            item.put("unit_amount", StringUtils.toNumeric(unitAmount));
            item.put("total_amount", StringUtils.toNumeric(unitAmount.multiply(new BigDecimal(qta))));
            item.put("tax_amount", StringUtils.toNumeric(taxAmount));
            item.put("discount_amount", discountAmount != null && !discountAmount.toString().equals("0") ? StringUtils.toNumeric(discountAmount) : "0");
            item.put("tax_percentage", product.getTaxPercentage() != null && !product.getTaxPercentage().toString().equals("0") ? StringUtils.toNumeric(product.getTaxPercentage()) : "0");
            item.put("net_unit_amount", StringUtils.toNumeric(netUnitAmount));
            item.put("gift_card_currency", product.getGiftCardCurrency());
            item.put("url", product.getUrl());
            item.put("image_url", product.getImageUrl());

            items.add(item);
        }

        return order.set("items", items);
    }

    private static JsonDoc setItemDetailsListForApm(AuthorizationBuilder builder, JsonDoc order) {
        BigDecimal taxTotalAmount = new BigDecimal(0);
        BigDecimal itemsAmount = new BigDecimal(0);
        BigDecimal orderAmount = new BigDecimal(0);

        if (builder.getMiscProductData() != null) {
            var items = new ArrayList<HashMap<String, Object>>();
            for (var product : builder.getMiscProductData()) {
                Integer qta = product.getQuantity() != null ? product.getQuantity() : 0;
                BigDecimal taxAmount = product.getTaxAmount() != null ? product.getTaxAmount() : new BigDecimal(0);
                BigDecimal unitAmount = product.getUnitPrice() != null ? product.getUnitPrice() : new BigDecimal(0);

                var item = new HashMap<String, Object>();

                item.put("reference", product.getProductId());
                item.put("label", product.getProductName());
                item.put("description", product.getDescription());
                item.put("quantity", qta);
                item.put("unit_amount", StringUtils.toNumeric(unitAmount));
                item.put("unit_currency", product.getUnitCurrency());
                item.put("tax_amount", StringUtils.toNumeric(taxAmount));
                item.put("amount", StringUtils.toNumeric(unitAmount.multiply(new BigDecimal(qta))));

                items.add(item);

                taxTotalAmount = taxTotalAmount.add(taxAmount);
                itemsAmount = itemsAmount.add(unitAmount);
            }

            order.set("tax_amount", StringUtils.toNumeric(taxTotalAmount));
            order.set("item_amount", StringUtils.toNumeric(itemsAmount));

            BigDecimal shippingAmount = builder.getShippingAmount() != null ? builder.getShippingAmount() : new BigDecimal(0);

            order.set("shipping_amount", StringUtils.toNumeric(builder.getShippingAmount()));
            order.set("insurance_offered", builder.getOrderDetails() != null ? (builder.getOrderDetails().hasInsurance() ? "YES" : "NO") : null);
            order.set("shipping_discount", StringUtils.toNumeric(builder.getShippingDiscount()));
            order.set("insurance_amount", StringUtils.toNumeric(builder.getOrderDetails().getInsuranceAmount()));
            order.set("handling_amount", StringUtils.toNumeric(builder.getOrderDetails().getHandlingAmount()));
            BigDecimal insuranceAmount = builder.getOrderDetails().getInsuranceAmount() != null ? builder.getOrderDetails().getInsuranceAmount() : new BigDecimal(0);
            BigDecimal handlingAmount = builder.getOrderDetails().getHandlingAmount() != null ?builder.getOrderDetails().getHandlingAmount() : new BigDecimal(0);

            orderAmount = itemsAmount.add(taxTotalAmount).add(handlingAmount).add(insuranceAmount).add(shippingAmount);

            order.set("amount", StringUtils.toNumeric(orderAmount));
            order.set("currency", builder.getCurrency());
            order.set("items", items);
        }

        return order;
    }

    private static JsonDoc setOrderInformation(AuthorizationBuilder builder, JsonDoc requestBody) {

        JsonDoc order;
        if (requestBody.has("order")) {
            order = requestBody.get("order");
        } else {
            order = new JsonDoc();
        }

        if (builder.getOrderDetails() != null) {
            order.set("description", builder.getOrderDetails().getDescription());
        }

        JsonDoc shippingAddress = new JsonDoc();

        if (builder.getShippingAddress() != null) {
            shippingAddress
                    .set("line_1", builder.getShippingAddress().getStreetAddress1())
                    .set("line_2", builder.getShippingAddress().getStreetAddress2())
                    .set("line_3", builder.getShippingAddress().getStreetAddress3())
                    .set("city", builder.getShippingAddress().getCity())
                    .set("postal_code", builder.getShippingAddress().getPostalCode())
                    .set("state", builder.getShippingAddress().getProvince())
                    .set("country", builder.getShippingAddress().getCountryCode());

            order.set("shipping_address", shippingAddress);
        }

        if (builder.getShippingPhone() != null) {
            var shippingPhone =
                    new JsonDoc()
                            .set("country_code", builder.getShippingPhone().getCountryCode())
                            .set("subscriber_number", builder.getShippingPhone().getNumber());

            order.set("shipping_phone", shippingPhone);
        }

        //AlternativePaymentMethod
        if (builder.getPaymentMethod() instanceof AlternativePaymentMethod) {
            if (builder.getMiscProductData() != null) {
                setItemDetailsListForApm(builder, order);
            }
        }

        //Buy Now Pay Later
        if (builder.getPaymentMethod() instanceof BNPL) {
            order
                    .set("shipping_method", builder.getBNPLShippingMethod() != null ? builder.getBNPLShippingMethod().toString() : null);

            if (builder.getMiscProductData() != null) {
                setItemDetailsListForBNPL(builder, order);
            }

            if (builder.getCustomerData() != null) {
                shippingAddress
                        .set("first_name", builder.getCustomerData().getFirstName())
                        .set("last_name", builder.getCustomerData().getLastName());
            }
        }

        order.set("shipping_address", shippingAddress);

        if (!requestBody.has("order")) {
            requestBody.set("order", order);
        }

        return requestBody;
    }

    private static String getCvvIndicator(CvnPresenceIndicator cvnPresenceIndicator) {
        if(cvnPresenceIndicator == null) return "";
        switch (cvnPresenceIndicator) {
            case Present:
                return "PRESENT";
            case Illegible:
                return "ILLEGIBLE";
            case NotOnCard:
                return "NOT_ON_CARD";
            default:
                return "NOT_PRESENT";
        }
    }

    private static String getChipCondition(EmvChipCondition emvChipCondition) {
        if (emvChipCondition == null) return "";
        switch (emvChipCondition) {
            case ChipFailPreviousSuccess:
                return "PREV_SUCCESS";
            case ChipFailPreviousFail:
                return "PREV_FAILED";
            default:
                return "UNKNOWN";
        }
    }

}