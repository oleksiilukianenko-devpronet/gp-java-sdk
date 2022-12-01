package com.global.api.network.enums;

import com.global.api.entities.enums.IStringConstant;
import com.global.api.utils.StringUtils;

public enum DE39_ActionCode implements IStringConstant {
    ;

    private final String value;
    DE39_ActionCode(String value) { this.value = value; }
    public String getValue() {
        return value;
    }
    public byte[] getBytes() {
        return value.getBytes();
    }

    public static String getDescription(String actionCode) {
        int code = -1;
        if(!StringUtils.isNullOrEmpty(actionCode)) {
            code =Integer.parseInt(actionCode);
        }

        switch (code) {
            case 0: return "Approved";
            case 1: return "Honor w/identification";
            case 2: return "Approved for partial amount";
            case 3: return "Approved (VIP)";
            case 4: return "Approved, update ICC";
            case 5: return "Approved, account type specified by card issuer";
            case 6: return "Approved for partial amount, account type specified by card issuer";
            case 7: return "Approved, update ICC";
            case 80: return "Approved, under floor limit; card issuer unavailable";
            case 81: return "Transaction accepted – use manual imprinter";
            case 82: return "Approved but not collected (VAPS only)";
            case 83: return "Partially approved but not collected (VAPS only)";
            case 100: return "Do not honor";
            case 101: return "Expired card";
            case 102: return "Suspected fraud";
            case 103: return "Contact acquirer";
            case 104: return "Restricted card";
            case 106: return "Allowable PIN tries exceeded";
            case 107: return "Refer to card issuer";
            case 108: return "Refer to card issuer special conditions";
            case 109: return "Invalid merchant";
            case 110: return "Invalid amount";
            case 111: return "Invalid card number";
            case 112: return "PIN data required";
            case 113: return "Unacceptable fee";
            case 114: return "No account of type requested";
            case 115: return "Requested function not supported";
            case 116: return "Insufficient funds";
            case 117: return "Incorrect PIN";
            case 118: return "Invalid card type";
            case 119: return "Transaction not permitted to cardholder";
            case 120: return "Transaction not permitted to terminal";
            case 121: return "Exceeds withdrawal amount limit";
            case 122: return "Security violation";
            case 123: return "Exceeds withdrawal frequency limit";
            case 125: return "Card not effective";
            case 126: return "Invalid PIN block";
            case 127: return "PIN length error";
            case 128: return "PIN key synch error";
            case 129: return "Suspected counterfeit card";
            case 180: return "Not on file";
            case 181: return "Transaction already adjusted";
            case 182: return "Target not found";
            case 183: return "Reserved for Heartland use";
            case 184: return "Invalid/missing driver ID";
            case 185: return "Invalid/missing odometer";
            case 186: return "Refer to card issuer (specific to partial approval processing)";
            case 200: return "Do not honor";
            case 201: return "Expired card";
            case 202: return "Suspected fraud";
            case 203: return "Card acceptor contact acquirer";
            case 204: return "Restricted card";
            case 205: return "Card acceptor call acquirer's security department";
            case 206: return "Allowable PIN tries exceeded";
            case 207: return "Special conditions";
            case 208: return "Lost card";
            case 209: return "Stolen card";
            case 210: return "Suspected counterfeit card";
            case 300: return "Successful";
            case 301: return "Not supported by receiver";
            case 302: return "Unable to locate record on file";
            case 303: return "Duplicate record, old record replaced";
            case 304: return "Field edit error";
            case 305: return "File locked out";
            case 306: return "Not successful";
            case 307: return "Format error";
            case 308: return "Duplicate, new record rejected";
            case 309: return "Unknown file";
            case 310: return "EMV PDL failure";
            case 380: return "Approved";
            case 381: return "Suspected fraud";
            case 382: return "Declined";
            case 383: return "Under investigation";
            case 384: return "Account already exists";
            case 385: return "Insufficient funds";
            case 386: return "Make offer";
            case 387: return "Acknowledged";
            case 388: return "Invalid location";
            case 400: return "Accepted";
            case 480: return "Already reversed";
            case 481: return "No target found";
            case 482: return "Duplicate reversal";
            case 500: return "Reconciled, in balance";
            case 501: return "Reconciled, out of balance, do not attempt error recovery";
            case 502: return "Amount not reconciled, totals provided";
            case 503: return "Totals not available";
            case 504: return "Not reconciled, totals provided";
            case 580: return "Reconciled, out of balance, attempt error recovery";
            case 581: return "Requested batch not available";
            case 582: return "Totals provided for an open batch";
            case 583: return "Problem closing transaction batch";
            case 584: return "Cannot reconcile at non-owning host";
            case 585: return "Batch details provided";
            case 586: return "Totals provided for a previously closed batch, which closed in balance";
            case 587: return "Totals provided for a previously closed batch, which closed out of balance";
            case 600: return "Accepted";
            case 601: return "Not able to trace back original transaction";
            case 602: return "Invalid reference number";
            case 603: return "Reference number/PAN incompatible";
            case 604: return "POS photograph is not available";
            case 605: return "Item supplied";
            case 606: return "Request cannot be fulfilled-required/requested documentation is not available";
            case 680: return "Accepted: all mail sent from host to POS";
            case 681: return "Accepted: more mail to send to POS";
            case 682: return "Accepted: unable to send mail to POS";
            case 700: return "Accepted";
            case 800: return "Accepted";
            case 900: return "Advice acknowledged, no financial liability accepted";
            case 901: return "Advice acknowledged, financial liability accepted";
            case 950: return "Violation of business arrangement";
            case 902: return "Invalid transaction";
            case 903: return "Re-enter transaction";
            case 904: return "Format error";
            case 905: return "Acquirer not supported by switch";
            case 906: return "Cut-over in progress";
            case 907: return "Card issuer or switch inoperative";
            case 908: return "Unknown transaction destination";
            case 909: return "System malfunction";
            case 910: return "Card issuer signed off";
            case 911: return "Card issuer timed-out";
            case 912: return "Card issuer unavailable";
            case 913: return "Duplicate transmission";
            case 914: return "Not able to find original transaction";
            case 915: return "Check point error";
            case 918: return "No communication keys available for use";
            case 919: return "Encryption key synch error";
            case 920: return "Security software/hardware error: try again";
            case 921: return "Security software/hardware error: no action";
            case 922: return "Message number out of sequence";
            case 923: return "Request in progress";
            case 940: return "Invalid POS";
            case 941: return "Duplicate transaction";
            case 942: return "OFAC (Office of Foreign Assets Control) database hit (terrorist name search)";
            case 943: return "Account information failure";
            case 944: return "Accepted";
            case 952: return "Service Error";
            case 953: return "Too many queued / no connection";
            default: return "Unknown action code";
        }
    }
}
