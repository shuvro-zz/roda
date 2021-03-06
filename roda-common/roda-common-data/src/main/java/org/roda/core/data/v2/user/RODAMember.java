/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.data.v2.user;

import java.util.Set;

import org.roda.core.data.v2.IsModelObject;
import org.roda.core.data.v2.index.IsIndexed;
import org.roda.core.data.v2.ip.HasId;

public interface RODAMember extends IsIndexed, IsModelObject, HasId {

  boolean isActive();

  boolean isUser();

  @Override
  String getId();

  String getName();

  String getFullName();

  Set<String> getAllRoles();

  Set<String> getDirectRoles();

  boolean isNameValid();

}
