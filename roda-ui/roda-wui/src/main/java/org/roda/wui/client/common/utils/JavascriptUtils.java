/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.common.utils;

import com.google.gwt.core.client.JavaScriptObject;

public class JavascriptUtils {

  public static native void runHighlighter() /*-{
		$wnd.console.log("running highlighter");
		$wnd.jQuery('pre code').each(function(i, block) {
			$wnd.console.log("run highlighter on a pre-code");
			$wnd.hljs.highlightBlock(block);
		});
  }-*/;

  public static native void runHighlighter(JavaScriptObject parent) /*-{
		$wnd.jQuery(parent).find('pre code').each(function(i, block) {
			$wnd.hljs.highlightBlock(block);
		});
  }-*/;

  public static native void runHighlighterOn(JavaScriptObject parent) /*-{
		$wnd.jQuery(parent).each(function(i, block) {
			$wnd.hljs.highlightBlock(block);
		});
  }-*/;
  


  public static native void slideToggle(String selector) /*-{
		$wnd.jQuery(selector).click(function() {
			$wnd.jQuery(this).next().slideToggle(300, function() {
				// Animation complete.
			});
		});
  }-*/;

  public static native void slideToggle(JavaScriptObject parent, String selector) /*-{
		$wnd.jQuery(parent).find(selector).click(function() {
			$wnd.jQuery(this).next().slideToggle(300, function() {
				// Animation complete.
			});
		});
  }-*/;

  public static native void smoothScroll() /*-{
		$wnd.jQuery('a[href^="#"]').on(
				'click',
				function(event) {
					var target = $wnd.jQuery(this.hash.replace(
							/(:|\.|\[|\]|,)/g, "\\$1"));
					if (target.length) {
						event.preventDefault();
						$wnd.jQuery('html, body').animate({
							scrollTop : target.offset().top
						}, 1000);
					} else {
						event.preventDefault();
						// TODO send error
						alert(this.hash + " not found");
					}
				});
  }-*/;

  public static native void smoothScroll(JavaScriptObject parent) /*-{
		$wnd.jQuery(parent).find('a[href^="#"]').on(
				'click',
				function(event) {
					var target = $wnd.jQuery(this.hash.replace(
							/(:|\.|\[|\]|,)/g, "\\$1"));
					if (target.length) {
						event.preventDefault();
						$wnd.jQuery('html, body').animate({
							scrollTop : target.offset().top
						}, 1000);
					} else {
						event.preventDefault();
						// TODO send error
						alert(this.hash + " not found");
					}
				});
  }-*/;

  public static native void smoothScrollSimple(JavaScriptObject parent) /*-{
		var target = $wnd.jQuery(parent);
		$wnd.jQuery('html, body').animate({
			scrollTop : target.offset().top
		}, 1000);
  }-*/;

  public static native void scrollToHeader() /*-{
		var target = $wnd.jQuery('.h1');
		$wnd.jQuery('html, body').animate({
			scrollTop : target.offset().top
		}, 10);
  }-*/;

  public static native void runMiniUploadForm() /*-{
		$wnd.console.log("running mini upload form");
		$wnd
				.jQuery(function() {

					var ul = $wnd.jQuery('#upload-list');

					$wnd.jQuery('#drop a').click(function() {
						// Simulate a click on the file input button
						// to show the file browser dialog
						$wnd.jQuery(this).parent().find('input').click();
					});

					// Initialize the jQuery File Upload plugin
					$wnd
							.jQuery('#upload')
							.fileupload(
									{
										// This element will accept file drag/drop uploading
										dropZone : $wnd.jQuery('#drop'),

										// This function is called when a file is added to the queue;
										// either via the browse button, or via drag/drop:
										add : function(e, data) {

											var tpl = $wnd
													.jQuery('<li class="working"><input type="text" value="0" data-width="30" data-height="30"'
															+ ' data-fgColor="#089de3" data-readOnly="1" data-bgColor="#3e4043" /><p></p><span class="icon"></span></li>');

											// Append the file name and file size
											tpl
													.find('p')
													.text(data.files[0].name)
													.append(
															'<span class="errorMessage"></span>')
													.append(
															'<i>'
																	+ formatFileSize(data.files[0].size)
																	+ '</i>');

											// Add the HTML to the UL element
											data.context = tpl.appendTo(ul);

											// Initialize the knob plugin
											tpl.find('input').knob();

											// Listen for clicks on the cancel icon
											tpl.find('span').click(function() {

												if (tpl.hasClass('working')) {
													jqXHR.abort();
												}

												tpl.fadeOut(function() {
													tpl.remove();
												});

											});

											// Automatically upload the file once it is added to the queue
											var jqXHR = data.submit();
										},

										progress : function(e, data) {

											// Calculate the completion percentage of the upload
											var progress = parseInt(data.loaded
													/ data.total * 100, 10);

											// Update the hidden input field and trigger a change
											// so that the jQuery knob plugin knows to update the dial
											data.context.find('input').val(
													progress).change();

											if (progress == 100) {
												data.context
														.removeClass('working');
											}
										},

										fail : function(e, data) {
											// Something has gone wrong!
											data.context.addClass('error');
											data.context[0].setAttribute(
													"data-toggle", "tooltip");
											data.context[0]
													.setAttribute(
															"title",
															data.jqXHR.responseJSON.message);
											data.context
													.find('span.errorMessage')
													.text(
															'('
																	+ data.jqXHR.responseJSON.message
																	+ ')');
											$wnd.console.log("data" + data
													+ " error thrown: "
													+ data.errorThrown);
										},
									});

					// Prevent the default action when a file is dropped on the window
					$wnd.jQuery(document).on('drop dragover', function(e) {
						e.preventDefault();
					});

					function getMethods(obj) {
						var result = [];
						for ( var id in obj) {
							try {
								if (typeof (obj[id]) == "function") {
									result.push(id + ": " + obj[id].toString());
								}
							} catch (err) {
								result.push(id + ": inaccessible");
							}
						}
						return result;
					}

					// Helper function that formats the file sizes
					function formatFileSize(bytes) {
						if (typeof bytes !== 'number') {
							return '';
						}

						if (bytes >= 1000000000) {
							return (bytes / 1000000000).toFixed(2) + ' GB';
						}

						if (bytes >= 1000000) {
							return (bytes / 1000000).toFixed(2) + ' MB';
						}

						return (bytes / 1000).toFixed(2) + ' KB';
					}

				});
  }-*/;

  public static native int isUploadRunning() /*-{
		var activeUploads = $wnd.jQuery('#upload').fileupload('active');
		return activeUploads;
  }-*/;

  public static native void updateURLWithoutReloading(String newUrl) /*-{
		$wnd.history.pushState(newUrl, "", newUrl);
  }-*/;

  public static native void toggleRightPanel(String panel) /*-{
		$wnd.jQuery(panel).animate({
			width : 'toggle'
		}, 100);
  }-*/;

  public static native void hideRightPanel(String panel) /*-{
		$wnd.jQuery(panel).hide();
  }-*/;

  public static native void historyGo(int n) /*-{
		$wnd.history.go(n);
  }-*/;

  public static native boolean isOnline() /*-{
		if ($wnd.navigator.onLine != undefined) {
			return $wnd.navigator.onLine;
		}
		return true;
  }-*/;

  public static native void changeLocale(String newLocale) /*-{
		var currLocation = $wnd.location.toString();
		var noHistoryCurrLocArray = currLocation.split("#");
		var noHistoryCurrLoc = noHistoryCurrLocArray[0];
		var locArray = noHistoryCurrLoc.split("?");
		$wnd.location.href = locArray[0] + "?locale=" + newLocale + "#"
				+ noHistoryCurrLocArray[1];
  }-*/;

  public static native void setCookieOptions(String message, String dismiss, String learnMore, String link) /*-{
		if ($wnd.update_cookieconsent_options) {
			$wnd.update_cookieconsent_options({
				"message" : message,
				"dismiss" : dismiss,
				"learnMore" : learnMore,
				"link" : link,
				"theme" : "dark-top",
			});
		}
  }-*/;

  public static native void runTextFill(String selector) /*-{
		$wnd.jQuery(selector).textfill({});
  }-*/;

  public static native void expose(String key, String value) /*-{
		$doc[key] = value;
  }-*/;
  
  public static native void stickInParent(JavaScriptObject object) /*-{
    $wnd.jQuery(object).stick_in_parent();
  }-*/;
  
  public static native void stickSidebar() /*-{
    $wnd.jQuery('.sticky-flow').stick_in_parent();
    $wnd.jQuery('body').trigger("sticky_kit:recalc");
  }-*/;
  
  public static native void stickRecalc() /*-{
    $wnd.jQuery('body').trigger("sticky_kit:recalc");
  }-*/;

  
  public static native JavaScriptObject runImageViewerOn(JavaScriptObject parent) /*-{
    var viewer = $wnd.jQuery(parent).ImageViewer();
    return viewer;
}-*/;
  
  public static native void stopImageViewer(JavaScriptObject imageViewerObject) /*-{
    console.log( "obj:" + imageViewerObject );
    imageViewerObject.destroy();
}-*/;
  

}
