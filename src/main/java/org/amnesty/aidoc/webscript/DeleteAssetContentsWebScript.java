package org.amnesty.aidoc.webscript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.util.ParameterCheck;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.amnesty.aidoc.AiIndex;
import org.amnesty.aidoc.Util;

public class DeleteAssetContentsWebScript extends BaseWebScript {

  /*
   * Spring service dependencies
   */
  protected NodeService nodeService;

  protected FileFolderService fileFolderService;

  protected PermissionService permissionService;
  
  public void setFileFolderService(FileFolderService fileFolderService) {
		this.fileFolderService = fileFolderService;
  }

  public void setNodeService(NodeService nodeService) {
      this.nodeService = nodeService;
  }
 
  public void setPermissionService( PermissionService permissionService )
  {
    this.permissionService = permissionService;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.alfresco.web.scripts.DeclarativeWebScript#executeImpl(org.alfresco.web.scripts.WebScriptRequest,
   *      org.alfresco.web.scripts.WebScriptResponse)
   */
  @Override
  protected Map<String, Object> executeAiImpl(WebScriptRequest req,
          Status status) {

      Map<String, Object> model = new HashMap<String, Object>(7, 1.0f);

      /*
       * Get and validate AiIndex parameters
       */
      String aiIndexStr = req.getParameter("aiIndex");


      logger.debug("Deleting contents of aiIndex=" + aiIndexStr);

      try
      {
        ParameterCheck.mandatoryString("aiIndex", aiIndexStr);
        
      } catch ( IllegalArgumentException e )
      {
        handleError(HttpServletResponse.SC_BAD_REQUEST,
            "Bad call to deleteassetcontents: " + e.getMessage(), e, model, status);
        return model;
      }


      /*
       * Lookup asset node, return error if not found
       */
      AiIndex aiIndex = AiIndex.parse(aiIndexStr);
      
      NodeRef assetRef = null;
      try
      {
      	 assetRef = Util.resolveAssetNode(
           		nodeService, fileFolderService, aiIndex.getYear(), aiIndex.getAiClass(), aiIndex.getDocnum());
      }
      catch (FileNotFoundException e)
      {
    	  handleError(HttpServletResponse.SC_NOT_FOUND,
                  "Cannot delete asset contents. No such asset as " + aiIndexStr , null, model, status);
          return model;
      }

      if ( permissionService.hasPermission( assetRef, PermissionService.DELETE_CHILDREN ) != AccessStatus.ALLOWED )
      {
        handleError(HttpServletResponse.SC_UNAUTHORIZED,
                "Cannot delete contents of asset " + aiIndexStr + " due to insufficient permissions" , null, model, status);
        return model;
      }  
      
      try
      {
       
        List<ChildAssociationRef> children = nodeService.getChildAssocs( assetRef );
        
        for( ChildAssociationRef child: children )
        {
          NodeRef childRef = child.getChildRef();
          
          nodeService.deleteNode( childRef );
        }
        
      } catch ( Exception e )
      {
        handleError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Cannot delete contents of asset " + aiIndexStr + " due to errors: " + e.getMessage(), e, model, status);
        return model;
      }

      logger.debug("asset contents deleted");

      model.put("code", HttpServletResponse.SC_OK);
      model.put("message", "SUCCESS");
      return model;
  }

}
