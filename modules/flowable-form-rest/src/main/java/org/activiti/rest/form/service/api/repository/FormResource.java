/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.rest.form.service.api.repository;

import javax.servlet.http.HttpServletRequest;

import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.form.api.Form;
import org.activiti.form.api.FormRepositoryService;
import org.activiti.rest.form.FormRestResponseFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Yvo Swillens
 */
@RestController
public class FormResource {

  @Autowired
  protected FormRestResponseFactory formRestResponseFactory;

  @Autowired
  protected FormRepositoryService formRepositoryService;

  @RequestMapping(value = "/form-repository/forms/{formId}", method = RequestMethod.GET, produces = "application/json")
  public FormResponse getForm(@PathVariable String formId, HttpServletRequest request) {
    Form form = formRepositoryService.getForm(formId);

    if (form == null) {
      throw new ActivitiObjectNotFoundException("Could not find a form with id '" + formId);
    }

    return formRestResponseFactory.createFormResponse(form);
  }
}
