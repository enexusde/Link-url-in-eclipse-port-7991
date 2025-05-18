package de.e_nexus.pde.prod.link;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Start at eclipse start the file handles http-requests on port
 * {@value Startup#PORT}, this is a static number and I do not contribute to any
 * discussions about the value.
 */
public class Startup implements IStartup {

	public static final int PORT = 7991;
	public static final String THREAD_NAME = "LinkURLEclipsePort" + PORT + "Acceptor";
	public static final String GET_PREFIX = "GET ";
	private static boolean shutdown = false;

	public static final ServerSocket serverSocket;
	static {
		ServerSocket lazyServerSocket = null;
		try {
			lazyServerSocket = new ServerSocket(PORT);
			lazyServerSocket.setSoTimeout(500);
		} catch (IOException e) {
		}
		serverSocket = lazyServerSocket;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown = true;
		}));
	}

	@Override
	public void earlyStartup() {
		Thread acceptor = new Thread(() -> {
			while (!shutdown) {
				try {
					var headers = new ArrayList<String>();
					try (var accept = serverSocket.accept()) {
						readHttpHeader(accept, headers);
						respondHttpRequest(accept.getOutputStream());
					}
					var httpMethodLine = headers.get(0);
					if (httpMethodLine.startsWith(GET_PREFIX)) {
						var absolutePath = httpMethodLine
								.subSequence(GET_PREFIX.length(), httpMethodLine.lastIndexOf(' ')).toString();
						var lineIndex = absolutePath.lastIndexOf('?') > -1
								? Integer.parseInt(absolutePath.substring(absolutePath.lastIndexOf('?') + 1))
								: null;
						if (lineIndex != null) {
							absolutePath = absolutePath.substring(0, absolutePath.lastIndexOf('?'));
						}
						var relativePath = absolutePath.startsWith("/") ? absolutePath.substring(1) : absolutePath;
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								try {
									var workbench = PlatformUI.getWorkbench();
									var targetWindow = workbench.getActiveWorkbenchWindow();

									if (targetWindow == null) {
										for (IWorkbenchWindow alternateWindow : workbench.getWorkbenchWindows()) {
											open(alternateWindow);
										}
									} else {
										open(targetWindow);
									}
								} catch (WorkbenchException exception) {
									ILog.get().error("Could not find workbench!", exception);
								}
							}

							private void open(IWorkbenchWindow workbenchWindow) throws PartInitException {
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart()
										.setFocus();
								var editorRegistry = workbenchWindow.getWorkbench().getEditorRegistry();
								var path = Paths.get(relativePath);
								var filenameEditors = editorRegistry
										.getEditors(path.getName(path.getNameCount() - 1).toString());
								var fullpathEditors = editorRegistry.getEditors(relativePath);

								var editorDescs = fullpathEditors.length > filenameEditors.length ? fullpathEditors
										: filenameEditors;

								if (editorDescs.length > 0) {
									var dominantDescriptor = editorDescs[0];
									var targetFile = ResourcesPlugin.getWorkspace().getRoot()
											.getFile(new org.eclipse.core.runtime.Path(relativePath));
									var editPart = IDE.openEditor(workbenchWindow.getActivePage(), targetFile,
											dominantDescriptor.getId(), false);
									if (lineIndex != null) {
										if (editPart instanceof FormEditor formEditor) {
											editPart = formEditor.getActiveEditor();
										}
										jumpToLine(lineIndex, editPart);
									}
								} else {
									ILog.get().error("No editor resolved for '" + relativePath + "'!");
								}
							}

							private void jumpToLine(Integer lineIndex, IEditorPart editPart) {
								if (editPart instanceof ITextEditor textEditor) {
									var document = textEditor.getDocumentProvider()
											.getDocument(textEditor.getEditorInput());
									try {
										var lineInformation = document.getLineInformation(lineIndex - 1);
										textEditor.selectAndReveal(lineInformation.getOffset(), 0);
									} catch (BadLocationException exception) {
										ILog.get().error("Could not find line '" + lineIndex + "'!", exception);
									}
								}
							}
						});
					}
				} catch (SocketTimeoutException e) {
					// Ignore
				} catch (Exception exception) {
					ILog.get().error("Something went wront!!", exception); // fault barrier
				}
			}
		}, THREAD_NAME);
		acceptor.start();
	}

	public void respondHttpRequest(OutputStream outputStream) {
		var out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.US_ASCII), false);
		out.println("HTTP/1.0 204 OK");
		out.println("Server: Eclipse LinkURLInEclipsePort" + PORT);
		out.println("Content-Length: 0");
		out.println("Content-Language: en");
		out.println("Connection: close");
		out.println("Content-Type: application/octet-stream");
		out.println();
		out.flush();
	}

	public void readHttpHeader(Socket accept, List<String> headers) throws IOException {
		var reader = new BufferedReader(new InputStreamReader(accept.getInputStream(), StandardCharsets.US_ASCII));
		do {
			var incomingLine = reader.readLine();
			if (incomingLine == null || incomingLine.isEmpty()) {
				break;
			}
			headers.add(incomingLine);
		} while (true);
	}
}