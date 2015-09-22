package org.roda.api.v1.entities;

import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "tasks")
public class TaskList {
  @XmlElements({@XmlElement(name = "task", type = String.class)})
  private List<String> tasks;

  public TaskList() {

  }

  public TaskList(String... values) {
    tasks = Arrays.asList(values);
  }

  public List<String> getTasks() {
    return tasks;
  }

  public void setTasks(List<String> tasks) {
    this.tasks = tasks;
  }

}
