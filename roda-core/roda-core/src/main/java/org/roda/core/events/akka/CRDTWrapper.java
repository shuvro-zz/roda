package org.roda.core.events.akka;

import org.roda.core.data.v2.IsRODAObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.cluster.ddata.AbstractReplicatedData;
import akka.cluster.ddata.ReplicatedDataSerialization;

public class CRDTWrapper extends AbstractReplicatedData<CRDTWrapper>
  implements IsRODAObject, ReplicatedDataSerialization {
  private static final long serialVersionUID = -9133998132086063749L;
  private static final Logger LOGGER = LoggerFactory.getLogger(CRDTWrapper.class);

  private IsRODAObject rodaObject;
  private boolean isUpdate;
  private String instanceId;
  private long timeinmillis;

  public CRDTWrapper(IsRODAObject rodaObject, boolean isUpdate, String instanceId, long timeinmillis) {
    this.rodaObject = rodaObject;
    this.isUpdate = isUpdate;
    this.instanceId = instanceId;
    this.setTimeinmillis(timeinmillis);
  }

  public CRDTWrapper() {
  }

  @Override
  public String getId() {
    return rodaObject.getId();
  }

  public IsRODAObject getRodaObject() {
    return rodaObject;
  }

  public boolean isUpdate() {
    return isUpdate;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public long getTimeinmillis() {
    return timeinmillis;
  }

  public void setTimeinmillis(long timeinmillis) {
    this.timeinmillis = timeinmillis;
  }

  @Override
  public CRDTWrapper mergeData(CRDTWrapper that) {
    if (!this.instanceId.equals(that.getInstanceId()) && this.timeinmillis > that.getTimeinmillis()) {
      LOGGER.warn("Maintaining local version: \nthis:{} \nthat:{}", this, that);
      return this;
    } else {
      return that;
    }
  }

  @Override
  public String toString() {
    return "CRDTWrapper [rodaObject=" + rodaObject + ", isUpdate=" + isUpdate + ", instanceId=" + instanceId
      + ", timeinmillis=" + timeinmillis + "]";
  }
}
