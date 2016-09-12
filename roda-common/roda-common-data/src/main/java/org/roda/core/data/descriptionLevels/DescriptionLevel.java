/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.data.descriptionLevels;

import java.io.Serializable;
import java.util.Map;

/**
 * 
 * 
 * @author Rui Castro
 * @author Hélder Silva
 * @author Luis Faria <lfaria@keep.pt>
 */
public class DescriptionLevel implements Serializable {
  private static final long serialVersionUID = 9038357012292858570L;

  // description level
  private String level = null;
  private Map<String, String> labels;
  private String iconClass;

  public DescriptionLevel() {
    super();
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }
  
  public String getIconClass() {
    return iconClass;
  }

  public void setIconClass(String iconClass) {
    this.iconClass = iconClass;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((iconClass == null) ? 0 : iconClass.hashCode());
    result = prime * result + ((labels == null) ? 0 : labels.hashCode());
    result = prime * result + ((level == null) ? 0 : level.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DescriptionLevel other = (DescriptionLevel) obj;
    if (iconClass == null) {
      if (other.iconClass != null)
        return false;
    } else if (!iconClass.equals(other.iconClass))
      return false;
    if (labels == null) {
      if (other.labels != null)
        return false;
    } else if (!labels.equals(other.labels))
      return false;
    if (level == null) {
      if (other.level != null)
        return false;
    } else if (!level.equals(other.level))
      return false;
    return true;
  }

  
  @Override
  public String toString() {
    return "DescriptionLevel [level=" + level + ", labels=" + labels + ", iconClass=" + iconClass + "]";
  }

  public String getLabel(String locale) {
    String label = null;
    if (labels != null) {
      label = labels.get(locale);
    }
    return label;
}
}