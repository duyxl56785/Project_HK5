# TV5 — scoped service-pair / Store / Finance QA

**REVIEW-AUDIT-20261018-r4. Refs #5/#12/#28/#39/#48.**
Target **TV1-S01-OD09-20261018-v1@c0b485a**, [sourcehandoff](https://github.com/duyxl56785/Project_HK5/blob/c0b485aa62ee3275fe56053bdc40497aedbd3a73/docs/TV1_S01_OD09_INTEGRATION_HANDOFF_2026-10-18.md).
TV2review5563376836 andTV3review5563408894 confirm **design scope**, không positive runtime. Agent chỉ lậpQA, khôngbusinesssignature.

## A. Đã kiểm trên sourcebe1d5b6

| Evidence | Result | Giới hạn |
|---|---|---|
|Order ContractJwtDecoderTest +JwtEnvironmentConfigurationTest, realYAML/env, missingkeyclosed, HS256/noJWKS|PASS trong88Orderunit|Localcrypto/config, synthetickeys; khôngAuthissuedJWT/currentrole/networkpair |
|FinancePolicyConfigurationTest|PASS trong88Orderunit|Defaultfalse; syntheticapproved fixture khôngbusinesssignoff |
|FinanceRules pureStoreOwner/settleability guards|PASS trong88Orderunit|KhôngactualcurrentStorequery/atomicsettlement/payment source |
|OriginalFIN01/06+control, realPG16|3PASS|Exactexistingstoragepreflight/replay/sideeffects, khôngM01approved/liveE2E |

Rawcasecounts theo [manifest](../reports/evidence/project-review-20261018/manifest.json); không đếm bảng này như testcases bổ sung.

## B. Ma trận implementation acceptance — chưa thực thi

Mỗi status **NOT_RUN/BLOCKED** bên dưới cần đúngruntimeprovider/filter/authority; đọcsource hoặcmockdeny không thànhlivePASS.
TV5 có thể viết contract harness khi owner đưa implementation/wire hợp lệ, không tự sửaCart/Inventory/Auth.

| ID | Expected behavior từTV1v1 | Owner / prerequisite | Current status |
|---|---|---|---|
|SP01|Order→Cart/Catalog/Inventory missing/wrong/cross-paircredential →401INTERNAL_AUTH_INVALID|TV1security+eachcallee/caller; dedicatedverifier|NOT_RUN/BLOCKED |
|SP02|Validpair wrongmethod/exactpath →403INTERNAL_FORBIDDEN; no wildcard|TV1+TV2/TV3/TV4filters|NOT_RUN/BLOCKED |
|SP03|OrderInventorypair onlyRESERVE/CONSUME/RELEASE; init/INCREASE/DECREASE deny403|TV4dedicatedpair/typefilter +TV3intent|NOT_RUN/BLOCKED; currentbroadtoken NOTcompatible |
|SP04|Missing/unavailableverifier/currentactor/source →503, no sideeffect|TV1+provider; verifiedcurrentstatus|NOT_RUN/BLOCKED |
|SP05|PairdoesnotreplaceoriginalcustomerBearer; JWTalonecannotinternal; forgedactor/Store/customer/rolesheaders ignored|TV1+TV3Cart,TV2Catalog|NOT_RUN/BLOCKED; localJWTunit không chứng minhHTTPpair |
|SP06|CustomerAcannotclaim/release/finalizeBcart; exactCartowner/idempotency|TV3scopedCartintegration/currentactor|NOT_RUN/BLOCKED |
|SP07|Backgroundcleanup/CONSUME/RELEASE uses approvedsystemprovenance, noforgedBearer|TV1+TV3/TV4 systemwire/journal|NOT_RUN/BLOCKED |
|SP08|StaleSELLERafterSUSPENDED orwrongStore denied; currentAPPROVED/owner required|TV1Auth+TV2Storecurrentprovider+consumers|NOT_RUN/BLOCKED |
|SP09|CurrentADMIN afterDELIVERED confirmsCOD once; customer/seller/staleADMIN denied|TV4COD+TV1livecurrentrole+TV3transaction|NOT_RUN/BLOCKED |
|SP10|StoreAlist/detail/aggregate cannotreadBFinance; clientstoreIdfilter nevergrant|TV5Finance whenauthorizedphase+TV2authority+TV1|NOT_RUN/BLOCKED; pureequalityguard isnotquery |
|SP11|Current+previouspairrotation accepted onlyinapprovedwindow; retired/crosspair denied|TV1+eachcallee operationalrotationwire|NOT_RUN/BLOCKED |
|SP12|GatewayneverroutesinternalAPI orforwardsclientInternalToken; noCORSleak|TV1Gatewayimplementation/config|NOT_RUN/BLOCKED |
|SP13|CatalogexplicitABSENT vsunavailable; completebatch/noREADYsubset; privacyerrorenvelope|TV2actualHTTP/verifiedsource+TV3caller+TV1privacy|NOT_RUN/BLOCKED positiveHTTP; internaltypedsourcefixverifiedhistoricallyseparate |
|SP14|PersistT1canonicalSKU/qty/type/operationId beforeHTTP, T2verifiedresult;exactretry/UNKNOWNhold|TV3journal+TV4operationresult+TV2SKUauthority|NOT_RUN/BLOCKED; InventoryoperationPGalone khôngOrderjournal |

**Hiện trạng source được đọc:** Cart InternalCartController dùng mộtcheckout-token và trả403 cho permissiondenial;
InventoryInternalTokenFilter bảo vệ `/api/inventory/**` bằng broadtoken và thiếucredential401/unconfigured503, chưatypedpairallowlist;
ProductSecurityConfiguration denyAll ngoàihealth. Đây là existingfailclosed/scaffold boundaries, **không phải SP01–14implemented**.
Không thêmcredentials/disableprotection để chạy positivecase. No liveAuth/Gateway/Store/COD/settlement/E2E result được suy từtestsởA.

### PR49 isolated filter follow-up (khôngmerged/runtimeenabled)

Headcf60281thêm dedicatedOrderpairfilter/config, exactPOST/typeallowlist vàbodywrapper. TV5chạy2securityclasses:
5cf4d75 **4PASS/1FAIL** vìtestcastwrapper; ownercf60281sửađúngHttpServletRequest, samepath/bodyassertions →**5PASS**,exit0/13s.
[Per-SHArawmanifest](../reports/evidence/project-review-20261018/manifest.json). SP01missing/wrongcredential/SP02pathmethod/SP03types/SP04unconfiguredverifier có **partialunit evidence**,
**không đổi bảngBthànhlivePASS**: thiếudeployedHTTP/filterordering/rotation/currentStore/delegation/systemprovenance.
Legacybroadtoken vẫnriêngkhôngcấpOrder; tokenmớigiữunset. Positiveunitpass với forgedheaders chỉchứngminhfilterkhôngdùngchúnglàidentity, khôngcurrentactorverification.

## C. Approval và nghiệm thu

- [x] MapTV1handoff → IDs/expected/owner/dependency/status và tách localunit khỏipair/live proof.
- [ ] Actualcallee+caller/currentprovider implementation theo đúngscope, approvedwire/version và ownerreview.
- [ ] ChạySP01–14 ởđúngSHA/môitrường, giữlệnh/log/XML/hash vàred/green thực; positivecase cầnAuthissuedtoken/providerthật.
- [ ] Businessapprover/operator đượcchủdựán chỉđịnh; Productprice MD01/02/unitcapMD03 kýriêng +schema/S01/security; không kýthayTV5/business.
- [ ] RuntimeFinance/COD/settlement/Storequeries/E2E acceptance khiđúngphase/scope đượcgiao.


## D. Current source after owner merges — 20/10/2026

This section supersedes historical source/pending-merge wording above, preserving the original18/10 snapshot.
[Current report and acceptance gates](../reports/TV5_ACCEPTANCE_PR39_54_56_2026-10-20.md).

- PR49/57 filter scaffold is now merged, with dedicated pair token/exact path/type/body cap and independent **default-OFF** flag. Historical broad-token-only statement is not current source. Broad legacy token still must never be given to Order.
- PR54 now provides Order-owned journal/scanner/caller scaffold; [fresh5955b1f](../reports/evidence/acceptance-20261020/pr54-summary.json) has96unit+23PGPASS, including7JournalPG cases. SP14 therefore has **verified local DB foundation**, while its complete approved trusted-input/HTTP/result/recovery requirement remains **NOT_RUN/BLOCKED live**. Do not mark the whole SP14 accepted.
- PR56 pure candidate helper8c88fff has [7fresh domainPASS](../reports/evidence/acceptance-20261020/pr56-summary.json); TV3 scoped candidate approval is recorded. This is not SP07system provenance, SP08Store/current actor, SP09COD or a shared C06 runtime approval.
- SP01–SP14 production/positive HTTP statuses in tableB remain NOT_RUN/BLOCKED. Local/mock/DB proof is separate; caller transport remains NotSent, no live current-user/system authorization or scoped Finance query was executed.
- Named business approver/operator, same-version M01/COD/refund decisions, trusted Product→SKU/current Store providers, attempt fencing and live two-Store E2E remain required. No policy/pair flag enablement from this checkpoint.
