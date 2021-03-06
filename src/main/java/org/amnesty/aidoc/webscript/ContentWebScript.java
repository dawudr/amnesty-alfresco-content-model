package org.amnesty.aidoc.webscript;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import net.sf.acegisecurity.BadCredentialsException;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.amnesty.aidoc.Asset;
import org.amnesty.aidoc.AssetManager;
import org.amnesty.aidoc.AssetRendition;
import org.amnesty.aidoc.Document;
import org.amnesty.aidoc.Edition;
import org.amnesty.aidoc.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ContentWebScript extends AbstractWebScript {

	// select a particular language variant?
	private boolean selectByLanguage;

	private boolean publicScope;

	private static final Log logger = LogFactory.getLog(ContentWebScript.class);

	protected FileFolderService fileFolderService;

	protected NodeService nodeService;

	protected ContentService contentService;

	@Override
	public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

		ContentReader reader = null;
		boolean inline;

		try {
			Map<String, String> args = req.getServiceMatch().getTemplateVars();

			if (logger.isDebugEnabled()) {
				logger.debug("[Public scope] " + publicScope);
				logger.debug("[Select by language] " + selectByLanguage);
			}

			String documentYear = Util.readDocumentYear(args.get("document_year"), true);
			String classCode = Util.readClassCode(args.get("class_code"), true);
			String documentNo = Util.readDocumentNo(args.get("document_no"), true);
			// n.b. language may be null: defaults to en
			
			logger.debug("[Document Year] " + documentYear);
			logger.debug("[Class Code] " + classCode);
			logger.debug("[Document No] " + documentNo);
			
			String selectedLanguage = Util.readSelectedLanguage(args.get("selected_language"), true);

			if (selectedLanguage == null || selectedLanguage.equals(""))
			{
				throw new IllegalArgumentException("Missing selectedLanguage parameter");
			}
			logger.debug("[Selected language] " + selectedLanguage);
			
			String format = args.get("format");
			if(format.equalsIgnoreCase("html") || format.equalsIgnoreCase("inline")) 
			{ 
				logger.debug("Inline Document "+format);
				inline = true;
			}
			else if(format.equalsIgnoreCase("attachment") || format.equalsIgnoreCase("master")) 
			{
				logger.debug("Offline Document "+format);
				inline = false;
			} 
			else 
			{
				//could happens if serving html content with img refs in the same path.
				logger.debug("Unknown format "+format);
				res.setStatus(404);
				res.getOutputStream().flush();
				res.getOutputStream().close();
				return;
			}

			boolean guest = req.isGuest();

			if ((guest == true) && (publicScope == false)) {
				throw new BadCredentialsException("Guest user is unauthorized");
			}

			// Get the Asset for the request index information
			NodeRef assetNode = Util.resolveAssetNode(nodeService, fileFolderService, documentYear, classCode, documentNo);

			Asset asset = AssetManager.getAsset(nodeService, assetNode, publicScope, selectByLanguage, selectedLanguage);

			if (logger.isDebugEnabled()) {
				logger.debug("Asset");
				logger.debug(asset.toString());
				Iterator<Edition> editionsIt = asset.getEditions().iterator();
				while (editionsIt.hasNext()) {
					logger.debug("Edition");
					logger.debug(editionsIt.next().toString());
				}
			}

			List<Document> documents = asset.getDocuments();

			if (publicScope && documents.size() == 0) {
				throw new FileNotFoundException("No effective documents");
			}

			AssetRendition assetRendition = new AssetRendition(contentService, documents, selectedLanguage);

				if (assetRendition.getSelectedRendition() == null) {
					throw new FileNotFoundException("No selected rendition");
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Selected Rendtion");
					logger.debug(assetRendition.getSelectedRendition().toString());
				}

			Document selectedDocument = null;
			if(inline) {
				selectedDocument = assetRendition.getSelectedRendition().getInlineDocument();				
				res.setContentType(assetRendition.getSelectedRendition().getInlineDocument().getMimetype()+"; charset=UTF-8");
				
				
			} else { 
				
				String filename = null;
				String contentType = null;
				if (format.equalsIgnoreCase("attachment")){
					selectedDocument = assetRendition.getSelectedRendition().getAttachmentDocument();
					filename = assetRendition.getSelectedRendition().getAttachmentFilename();
					contentType = assetRendition.getSelectedRendition().getAttachmentDocument().getMimetype();
					
				} 
				else if(format.equalsIgnoreCase("master"))
				{
				selectedDocument = assetRendition.getSelectedRendition().getMasterDocument();
				filename = assetRendition.getSelectedRendition().getMasterFilename();
				contentType = assetRendition.getSelectedRendition().getMasterDocument().getMimetype();
				}
				res.setContentType(contentType);
	            res.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");	
	            res.setHeader("Cache-Control", "private");
	            res.setHeader("Pragma", "private");
			}
			
			if(logger.isDebugEnabled())
			{
				logger.debug(selectedDocument.toString());
			}
			
			ContentData contentData = selectedDocument.getContent();
			
			if (contentData != null) {
				logger.debug("Content Data found");
				String contenturl = contentData.getContentUrl();

				reader = contentService.getRawReader(contenturl);
				if ((reader == null) || (!(reader.exists()))) {
					throw new FileNotFoundException("Unable to locate content for " + contenturl);
				} else {
					logger.debug("Content Found: " + contenturl);

				}
			}			
			
			reader.getContent(res.getOutputStream());
			
			res.getOutputStream().flush();
			res.getOutputStream().close();
			
		} catch (AccessDeniedException e) {
			throw new WebScriptException(HttpServletResponse.SC_FORBIDDEN, e.getMessage(), null, res);
		} catch (BadCredentialsException e) {
			throw new WebScriptException(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage(), null, res);
		} catch (IllegalArgumentException e) {
			throw new WebScriptException(HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null, res);
		} catch (FileNotFoundException e) {
			throw new WebScriptException(HttpServletResponse.SC_NOT_FOUND, e.getMessage(), null, res);
		} catch (Exception e) {
			logger.error(e);
			throw new WebScriptException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), null, res);
		}
	}

	public boolean isPublicScope() {
		return publicScope;
	}

	public void setPublicScope(boolean publicScope) {
		this.publicScope = publicScope;
	}

	public boolean isSelectByLanguage() {
		return selectByLanguage;
	}

	public void setSelectByLanguage(boolean selectByLanguage) {
		this.selectByLanguage = selectByLanguage;
	}

	public FileFolderService getFileFolderService() {
		return fileFolderService;
	}

	public void setFileFolderService(FileFolderService fileFolderService) {
		this.fileFolderService = fileFolderService;
	}

	public NodeService getNodeService() {
		return nodeService;
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public ContentService getContentService() {
		return contentService;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}
}