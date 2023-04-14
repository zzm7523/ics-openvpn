/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 类似于jetty中的HttpURI实现
 */
public final class HttpURI implements Cloneable, Serializable {

	private static final long serialVersionUID = 5749802846025066157L;

	private static final int START = 0, AUTH_OR_PATH = 1, SCHEME_OR_PATH = 2, AUTH = 4, IPV6 = 5, PORT = 6,
			PATH = 7, PARAM = 8, QUERY = 9, ASTERISK = 10;

	private transient boolean _partial = false;
	private transient int scheme_start_idx;
	private transient int authority_start_idx;
	private transient int host_start_idx;
	private transient int port_start_idx;
	private transient int path_start_idx;
	private transient int param_start_idx;
	private transient int query_start_idx;
	private transient int fragment_start_idx;
	private transient int end_idx;

	// Components of all URIs: [<scheme>:]<scheme-specific-part>[#<fragment>]
	private transient String scheme; // null ==> relative URI
	private transient String fragment;

	// Hierarchical URI components: [//<authority>]<path>[?<query>]
	private transient String authority; // Registry or server

	// Server-based authority: [<userInfo>@]<host>[:<port>]
	private transient String userInfo;
	private transient String host; // null ==> registry-based
	private transient int port = -1; // -1 ==> undefined

	// Remaining components of hierarchical URIs
	private transient String path; // null ==> opaque
	private transient String query;

	// The remaining fields may be computed on demand
	private volatile transient int hash = 0; // Zero ==> undefined
	private volatile transient Map<String, String[]> paramMap;

	private String rawString;

	public static HttpURI create(final String rawString) {
		return new HttpURI(rawString);
	}

	public HttpURI(final String rawString) {
		this.rawString = rawString;
		parse(rawString);
	}

	public HttpURI(final String scheme, final String authority, final String path, final String query,
			final String fragment) {
		this(scheme, authority, null, -1, path, query, fragment);
	}

	public HttpURI(final String scheme, final String userInfo, final String host, int port, final String path,
			final String query, final String fragment) {
		this.scheme = scheme.toLowerCase();
		this.userInfo = userInfo;
		this.host = host;
		if (port == -1) {
			this.port = -1;
		} else {
			if ((port == 443 && "https".equals(scheme)) || (port == 80 && "http".equals(scheme))) {
				this.port = -1;
			} else {
				this.port = port;
			}
		}
		this.path = path;
		this.query = query;
		this.fragment = fragment;
	}

	public String getScheme() {
		if (scheme == null && scheme_start_idx < authority_start_idx) {
			int l = authority_start_idx - scheme_start_idx;
			if (l == 5 && (rawString.charAt(scheme_start_idx) == 'h' || rawString.charAt(scheme_start_idx) == 'H') &&
					(rawString.charAt(scheme_start_idx + 1) == 't' || rawString.charAt(scheme_start_idx + 1) == 'T') &&
					(rawString.charAt(scheme_start_idx + 2) == 't' || rawString.charAt(scheme_start_idx + 2) == 'T') &&
					(rawString.charAt(scheme_start_idx + 3) == 'p' || rawString.charAt(scheme_start_idx + 3) == 'P')) {
				scheme = "http";
			}
			if (l == 6 && (rawString.charAt(scheme_start_idx) == 'h' || rawString.charAt(scheme_start_idx) == 'H') &&
					(rawString.charAt(scheme_start_idx + 1) == 't' || rawString.charAt(scheme_start_idx + 1) == 'T') &&
					(rawString.charAt(scheme_start_idx + 2) == 't' || rawString.charAt(scheme_start_idx + 2) == 'T') &&
					(rawString.charAt(scheme_start_idx + 3) == 'p' || rawString.charAt(scheme_start_idx + 3) == 'P') &&
					(rawString.charAt(scheme_start_idx + 4) == 's' || rawString.charAt(scheme_start_idx + 4) == 'S')) {
				scheme = "https";
			}
		}
		return scheme;
	}

	public boolean isAbsolute() {
		return getScheme() != null;
	}

	public boolean isOpaque() {
		return getPath() == null;
	}

	public String getHost() {
		if (host == null && host_start_idx < port_start_idx) {
			host = rawString.substring(host_start_idx, port_start_idx);
		}
		return host;
	}

	public int getPort() {
		if (port == -1 && port_start_idx < path_start_idx) {
			port = Integer.parseInt(rawString.substring(port_start_idx + 1, path_start_idx));
			if ((port == 443 && "https".equals(scheme)) || (port == 80 && "http".equals(scheme))) {
				port = -1;
			}
		}
		return port;
	}

	public String getAuthority() {
		return authority;
	}

	public String getUserInfo() {
		return userInfo;
	}

	public String getPath() {
		if (path == null && path_start_idx < param_start_idx) {
			path = rawString.substring(path_start_idx, param_start_idx);
		}
		return path;
	}

	public String getQuery() {
		if (query == null && query_start_idx < fragment_start_idx) {
			query = rawString.substring(query_start_idx + 1, fragment_start_idx);
		}
		return query;
	}

	public String getFragment() {
		if (fragment == null && fragment_start_idx < end_idx) {
			fragment = rawString.substring(fragment_start_idx + 1, end_idx);
		}
		return fragment;
	}

	public Map<String, String[]> getParameterMap() {
		if (paramMap == null) {
			paramMap = new LinkedHashMap<String, String[]>();
			if (getQuery() != null && !getQuery().isEmpty()) {
				splitQueryTo(getQuery(), paramMap);
			}
		}
		return paramMap;
	}

	public HttpURI resolve(HttpURI uri) {
		return resolve(this, uri);
	}

	public HttpURI resolve(String str) {
		return resolve(HttpURI.create(str));
	}

	public HttpURI normalize() {
		if (isOpaque() || (getPath() == null) || (getPath().length() == 0)) {
			return this;
		}

		String np = normalize(getPath());
		if (np == getPath()) {
			return this;
		} else {
			HttpURI v = new HttpURI();
			v.scheme = getScheme();
			v.fragment = getFragment();
			v.authority = getAuthority();
			v.userInfo = getUserInfo();
			v.host = getHost();
			v.port = getPort();
			v.path = np;
			v.query = getQuery();
			return v;
		}
	}

	public URL toURL() throws MalformedURLException {
		if (!isAbsolute()) {
			throw new IllegalArgumentException("HttpURI is not absolute");
		}
		return new URL(toString());
	}

	@Override
	public String toString() {
		if (rawString == null) {
			StringBuilder buffer = new StringBuilder(128);
			if (getHost() != null) {
				if (getScheme() != null) {
					buffer.append(getScheme()).append("://");
				}
				buffer.append(getHost());
				if (getPort() != -1) {
					buffer.append(':').append(getPort());
				}
			}
			if (getPath() == null || !getPath().startsWith("/")) {
				buffer.append('/');
			}
			if (getPath() != null) {
				buffer.append(getPath());
			}
			if (getQuery() != null) {
				buffer.append('?').append(getQuery());
			}
			if (getFragment() != null) {
				buffer.append('#').append(getFragment());
			}
			rawString = buffer.toString();
		}
		return rawString;
	}

	public String toASCIIString() {
		return toString();
	}

	@Override
	public int hashCode() {
		if (hash == 0) {
			// 协议忽略大小写
			if (getScheme() != null) {
				hash += getScheme().toLowerCase().hashCode();
			}
			// 主机名忽略大小写
			if (getHost() != null) {
				hash += getHost().toLowerCase().hashCode();
			}
			hash += getPort();

			// 路径区分大小写
			if (getPath() != null) {
				hash += getPath().hashCode();
			}
			// 查询参数忽略顺序, 并且区分大小写
			if (getQuery() != null) {
				Map<String, String[]> paramMap = getParameterMap();
				Iterator<Map.Entry<String, String[]>> it = paramMap.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, String[]> e = it.next();
					hash += e.getKey().hashCode();
					hash += Arrays.hashCode(e.getValue());
				}
			}
			if (getFragment() != null) {
				hash += getFragment().hashCode();
			}
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		HttpURI other = (HttpURI) obj;
		if (getScheme() == null) {
			if (other.getScheme() != null) {
				return false;
			}
		} else if (!getScheme().equals(other.getScheme())) {
			return false;
		}
		if (getHost() == null) {
			if (other.getHost() != null) {
				return false;
			}
		} else if (!getHost().equalsIgnoreCase(other.getHost())) {
			return false;
		}
		if (getPort() != other.getPort()) {
			return false;
		}

		if (getPath() == null) {
			if (other.getPath() != null) {
				return false;
			}
		} else if (!getPath().equals(other.getPath())) {
			return false;
		}

		if (getUserInfo() == null) {
			if (other.getUserInfo() != null) {
				return false;
			}
		} else if (!getUserInfo().equals(other.getUserInfo())) {
			return false;
		}
		if (getAuthority() == null) {
			if (other.getAuthority() != null) {
				return false;
			}
		} else if (!getAuthority().equals(other.getAuthority())) {
			return false;
		}

		if (getFragment() == null) {
			if (other.getFragment() != null) {
				return false;
			}
		} else if (!getFragment().equals(other.getFragment())) {
			return false;
		}
		if (getQuery() == null) {
            return other.getQuery() == null;
		} else {
            return equalsQuery(this.getParameterMap(), other.getParameterMap());
		}
    }

	private HttpURI() {
	}

	private void parse(final String rawString) {
		int i = 0;
		int e = rawString.length();
		int state = START;
		int m = 0;

		end_idx = rawString.length();
		scheme_start_idx = 0;
		authority_start_idx = 0;
		host_start_idx = 0;
		port_start_idx = 0;
		path_start_idx = 0;
		param_start_idx = end_idx;
		query_start_idx = end_idx;
		fragment_start_idx = end_idx;

		while (i < e) {
			char c = rawString.charAt(i);
			int s = i++;

			state: switch (state) {
			case START: {
				m = s;
				switch (c) {
				case '/':
					state = AUTH_OR_PATH;
					break;
				case ';':
					param_start_idx = s;
					state = PARAM;
					break;
				case '?':
					param_start_idx = s;
					query_start_idx = s;
					state = QUERY;
					break;
				case '#':
					param_start_idx = s;
					query_start_idx = s;
					fragment_start_idx = s;
					break;
				case '*':
					path_start_idx = s;
					state = ASTERISK;
					break;
				default:
					if (Character.isLetterOrDigit(c) || c == '.') {
						state = SCHEME_OR_PATH;
					} else {
						throw new IllegalArgumentException(rawString);
					}
				}
				continue;
			}

			case AUTH_OR_PATH: {
				if ((_partial || scheme_start_idx != authority_start_idx) && c == '/') {
					host_start_idx = i;
					port_start_idx = end_idx;
					path_start_idx = end_idx;
					state = AUTH;
				} else if (c == ';' || c == '?' || c == '#') {
					i--;
					state = PATH;
				} else {
					host_start_idx = m;
					port_start_idx = m;
					state = PATH;
				}
				continue;
			}

			case SCHEME_OR_PATH: {
				// short cut for http and https
				if (rawString.length() > 6 && c == 't') {
					if (rawString.charAt(3) == ':') {
						s = 3;
						i = 4;
						c = ':';
					} else if (rawString.charAt(4) == ':') {
						s = 4;
						i = 5;
						c = ':';
					} else if (rawString.charAt(5) == ':') {
						s = 5;
						i = 6;
						c = ':';
					}
				}

				switch (c) {
				case ':':
					m = i++;
					authority_start_idx = m;
					path_start_idx = m;
					c = rawString.charAt(i);
					if (c == '/') {
						state = AUTH_OR_PATH;
					} else {
						host_start_idx = m;
						port_start_idx = m;
						state = PATH;
					}
					break;
				case '/':
					state = PATH;
					break;
				case ';':
					param_start_idx = s;
					state = PARAM;
					break;
				case '?':
					param_start_idx = s;
					query_start_idx = s;
					state = QUERY;
					break;
				case '#':
					param_start_idx = s;
					query_start_idx = s;
					fragment_start_idx = s;
					break;
				}
				continue;
			}

			case AUTH: {
				switch (c) {
				case '/':
					m = s;
					path_start_idx = m;
					port_start_idx = path_start_idx;
					state = PATH;
					break;
				case '@':
					host_start_idx = i;
					break;
				case ':':
					port_start_idx = s;
					state = PORT;
					break;
				case '[':
					state = IPV6;
					break;
				}
				continue;
			}

			case IPV6: {
				switch (c) {
				case '/':
					throw new IllegalArgumentException("No closing ']' for " + rawString);
				case ']':
					state = AUTH;
					break;
				}
				continue;
			}

			case PORT: {
				if (c == '/') {
					m = s;
					path_start_idx = m;
					if (port_start_idx <= authority_start_idx) {
						port_start_idx = path_start_idx;
					}
					state = PATH;
				}
				continue;
			}

			case PATH: {
				switch (c) {
				case ';':
					param_start_idx = s;
					state = PARAM;
					break;
				case '?':
					param_start_idx = s;
					query_start_idx = s;
					state = QUERY;
					break;
				case '#':
					param_start_idx = s;
					query_start_idx = s;
					fragment_start_idx = s;
					break state;
				}
				continue;
			}

			case PARAM: {
				switch (c) {
				case '?':
					query_start_idx = s;
					state = QUERY;
					break;
				case '#':
					query_start_idx = s;
					fragment_start_idx = s;
					break state;
				}
				continue;
			}

			case QUERY: {
				if (c == '#') {
					fragment_start_idx = s;
					break state;
				}
				continue;
			}

			case ASTERISK: {
				throw new IllegalArgumentException("only '*'");
			}
			}

		}
	}

	private boolean equalsQuery(final Map<String, String[]> paramMap1, final Map<String, String[]> paramMap2) {
		if (paramMap1.size() != paramMap2.size()) {
			return false;
		}

		try {
			Iterator<Map.Entry<String, String[]>> it = paramMap1.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, String[]> e = it.next();
				String key = e.getKey();
				String[] value = e.getValue();
				if (value == null) {
					if (!(paramMap2.get(key)==null && paramMap2.containsKey(key))) {
						return false;
					}
				} else {
					if (!Arrays.equals(value, paramMap2.get(key))) {
						return false;
					}
				}
			}

		} catch (ClassCastException unused) {
			return false;
		} catch (NullPointerException unused) {
			return false;
		}

		return true;
	}

	// RFC2396 5.2
	private HttpURI resolve(HttpURI base, HttpURI child) {
		// check if child if opaque first so that NPE is thrown
		// if child is null.
		if (child.isOpaque() || base.isOpaque()) {
			return child;
		}

		// 5.2 (2): Reference to current document (lone fragment)
		if ((child.getScheme() == null) && (child.getAuthority() == null)
				&& "".equals(child.getPath()) && (child.getFragment() != null)
				&& (child.getQuery() == null)) {
			if ((base.getFragment() != null) && child.getFragment().equals(base.getFragment())) {
				return base;
			}
			HttpURI ru = new HttpURI();
			ru.scheme = base.getScheme();
			ru.authority = base.getAuthority();
			ru.userInfo = base.getUserInfo();
			ru.host = base.getHost();
			ru.port = base.getPort();
			ru.path = base.getPath();
			ru.fragment = child.getFragment();
			ru.query = base.getQuery();
			return ru;
		}

		// 5.2 (3): Child is absolute
		if (child.getScheme() != null) {
			return child;
		}
		HttpURI ru = new HttpURI();		// Resolved URI
		ru.scheme = base.getScheme();
		ru.query = child.getQuery();
		ru.fragment = child.getFragment();

		// 5.2 (4): Authority
		if (child.authority == null) {
			ru.authority = base.getAuthority();
			ru.host = base.getHost();
			ru.userInfo = base.getUserInfo();
			ru.port = base.getPort();

			String cp = (child.getPath() == null) ? "" : child.getPath();
			if ((cp.length() > 0) && (cp.charAt(0) == '/')) {
				// 5.2 (5): Child path is absolute
				ru.path = child.getPath();
			} else {
				// 5.2 (6): Resolve relative path
				ru.path = resolvePath(base.getPath(), cp, base.isAbsolute());
			}
		} else {
			ru.authority = child.getAuthority();
			ru.userInfo = child.getUserInfo();
			ru.host = child.getHost();
			ru.port = child.getPort();
			ru.path = child.getPath();
		}

		// 5.2 (7): Recombine (nothing to do here)
		return ru;
	}

	private static String resolvePath(String base, String child, boolean absolute) {
		int i = base.lastIndexOf('/');
		int cn = child.length();
		String path = "";

		if (cn == 0) {
			// 5.2 (6a)
			if (i >= 0)
				path = base.substring(0, i + 1);
		} else {
			StringBuffer sb = new StringBuffer(base.length() + cn);
			// 5.2 (6a)
			if (i >= 0)
				sb.append(base.substring(0, i + 1));
			// 5.2 (6b)
			sb.append(child);
			path = sb.toString();
		}

		// 5.2 (6c-f)
		String np = normalize(path);

		// 5.2 (6g): If the result is absolute but the path begins with "../",
		// then we simply leave the path as-is

		return np;
	}

	private static String normalize(String ps) {
		// Does this path need normalization?
		int ns = needsNormalization(ps);	// Number of segments
		if (ns < 0) {
			// Nope -- just return it
			return ps;
		}

		char[] path = ps.toCharArray();		// Path in char-array form
		// Split path into segments
		int[] segs = new int[ns];		// Segment-index array
		split(path, segs);

		// Remove dots
		removeDots(path, segs);

		// Prevent scheme-name confusion
		maybeAddLeadingDot(path, segs);

		// Join the remaining segments and return the result
		String s = new String(path, 0, join(path, segs));
		if (s.equals(ps)) {
			// string was already normalized
			return ps;
		}
		return s;
	}

	static private int needsNormalization(String path) {
		boolean normal = true;
		int ns = 0; // Number of segments
		int end = path.length() - 1; // Index of last char in path
		int p = 0; // Index of next char in path

		// Skip initial slashes
		while (p <= end) {
			if (path.charAt(p) != '/')
				break;
			p++;
		}
		if (p > 1)
			normal = false;

		// Scan segments
		while (p <= end) {

			// Looking at "." or ".." ?
			if ((path.charAt(p) == '.')
					&& ((p == end) || ((path.charAt(p + 1) == '/') || ((path.charAt(p + 1) == '.') && ((p + 1 == end) ||
							(path.charAt(p + 2) == '/')))))) {
				normal = false;
			}
			ns++;

			// Find beginning of next segment
			while (p <= end) {
				if (path.charAt(p++) != '/')
					continue;

				// Skip redundant slashes
				while (p <= end) {
					if (path.charAt(p) != '/')
						break;
					normal = false;
					p++;
				}

				break;
			}
		}

		return normal ? -1 : ns;
	}

	// Split the given path into segments, replacing slashes with nulls and
	// filling in the given segment-index array.
	//
	// Preconditions:
	// segs.length == Number of segments in path
	//
	// Postconditions:
	// All slashes in path replaced by '\0'
	// segs[i] == Index of first char in segment i (0 <= i < segs.length)
	//
	static private void split(char[] path, int[] segs) {
		int end = path.length - 1; // Index of last char in path
		int p = 0; // Index of next char in path
		int i = 0; // Index of current segment

		// Skip initial slashes
		while (p <= end) {
			if (path[p] != '/')
				break;
			path[p] = '\0';
			p++;
		}

		while (p <= end) {

			// Note start of segment
			segs[i++] = p++;

			// Find beginning of next segment
			while (p <= end) {
				if (path[p++] != '/')
					continue;
				path[p - 1] = '\0';

				// Skip redundant slashes
				while (p <= end) {
					if (path[p] != '/')
						break;
					path[p++] = '\0';
				}
				break;
			}
		}

		if (i != segs.length)
			throw new InternalError(); // ASSERT
	}

	// Join the segments in the given path according to the given segment-index
	// array, ignoring those segments whose index entries have been set to -1,
	// and inserting slashes as needed. Return the length of the resulting
	// path.
	//
	// Preconditions:
	// segs[i] == -1 implies segment i is to be ignored
	// path computed by split, as above, with '\0' having replaced '/'
	//
	// Postconditions:
	// path[0] .. path[return value] == Resulting path
	//
	static private int join(char[] path, int[] segs) {
		int ns = segs.length; // Number of segments
		int end = path.length - 1; // Index of last char in path
		int p = 0; // Index of next path char to write

		if (path[p] == '\0') {
			// Restore initial slash for absolute paths
			path[p++] = '/';
		}

		for (int i = 0; i < ns; i++) {
			int q = segs[i]; // Current segment
			if (q == -1)
				// Ignore this segment
				continue;

			if (p == q) {
				// We're already at this segment, so just skip to its end
				while ((p <= end) && (path[p] != '\0'))
					p++;
				if (p <= end) {
					// Preserve trailing slash
					path[p++] = '/';
				}
			} else if (p < q) {
				// Copy q down to p
				while ((q <= end) && (path[q] != '\0'))
					path[p++] = path[q++];
				if (q <= end) {
					// Preserve trailing slash
					path[p++] = '/';
				}
			} else
				throw new InternalError(); // ASSERT false
		}

		return p;
	}

	// Remove "." segments from the given path, and remove segment pairs
	// consisting of a non-".." segment followed by a ".." segment.
	//
	private static void removeDots(char[] path, int[] segs) {
		int ns = segs.length;
		int end = path.length - 1;

		for (int i = 0; i < ns; i++) {
			int dots = 0; // Number of dots found (0, 1, or 2)

			// Find next occurrence of "." or ".."
			do {
				int p = segs[i];
				if (path[p] == '.') {
					if (p == end) {
						dots = 1;
						break;
					} else if (path[p + 1] == '\0') {
						dots = 1;
						break;
					} else if ((path[p + 1] == '.') && ((p + 1 == end) || (path[p + 2] == '\0'))) {
						dots = 2;
						break;
					}
				}
				i++;
			} while (i < ns);
			if ((i > ns) || (dots == 0))
				break;

			if (dots == 1) {
				// Remove this occurrence of "."
				segs[i] = -1;
			} else {
				// If there is a preceding non-".." segment, remove both that
				// segment and this occurrence of ".."; otherwise, leave this
				// ".." segment as-is.
				int j;
				for (j = i - 1; j >= 0; j--) {
					if (segs[j] != -1)
						break;
				}
				if (j >= 0) {
					int q = segs[j];
					if (!((path[q] == '.') && (path[q + 1] == '.') && (path[q + 2] == '\0'))) {
						segs[i] = -1;
						segs[j] = -1;
					}
				}
			}
		}
	}

	// DEVIATION: If the normalized path is relative, and if the first
	// segment could be parsed as a scheme name, then prepend a "." segment
	//
	private static void maybeAddLeadingDot(char[] path, int[] segs) {
		if (path[0] == '\0')
			// The path is absolute
			return;

		int ns = segs.length;
		int f = 0; // Index of first segment
		while (f < ns) {
			if (segs[f] >= 0)
				break;
			f++;
		}
		if ((f >= ns) || (f == 0))
			// The path is empty, or else the original first segment survived,
			// in which case we already know that no leading "." is needed
			return;

		int p = segs[f];
		while ((p < path.length) && (path[p] != ':') && (path[p] != '\0'))
			p++;
		if (p >= path.length || path[p] == '\0')
			// No colon in first segment, so no "." needed
			return;

		// At this point we know that the first segment is unused,
		// hence we can insert a "." segment at that position
		path[0] = '.';
		path[1] = '\0';
		segs[0] = 0;
	}

    // 如果参数存在=分割符分割name和value, 则返回true否则返回false; 例如:
    // 输入: /combo?3.3.0/build/oop/oop-min.js&3.3.0/build/event-custom/event-custom-base-min.js		返回false
    // 输入: /combo?3.3.0/build/oop/oop-min.js=&3.3.0/build/event-custom/event-custom-base-min.js=	返回true
    private boolean splitQueryTo(final String query, final Map<String, String[]> paramMap) {
        String name = null, value = null;
        String[] value_array = null;
        boolean has_equals = false;
        char c = 0;
        StringBuilder buffer = new StringBuilder(64);

        for (int i=0; i<query.length(); ++i) {
            c = query.charAt(i);
            switch (c) {
                case '&':
                    value = buffer.length() == 0 ? "" : buffer.toString();
                    buffer.setLength(0);
                    if (name != null) {
                        if (value == null) {
                            value = "";
                        }
                    } else if (value != null && !value.isEmpty()) {
                        name = value;
                        value = "";
                    }
                    if (name != null && value != null) {
                        value_array = paramMap.get(name);
                        if (value_array == null) {
                            value_array = new String[] { value };
                        } else {
                            value_array = addParamValue(value_array, value);
                        }
                        paramMap.put(name, value_array);
                    }
                    name = null;
                    value = null;
                    break;

                case '=':
                    has_equals = true;
                    if (name != null) {
                        buffer.append(c);
                    } else {
                        name = buffer.toString();
                        buffer.setLength(0);
                    }
                    break;

                default:
                    buffer.append(c);
                    break;
            }
        }

        if (name != null) {
            value = buffer.length() == 0 ? "" : buffer.toString();
        } else if (buffer.length() > 0) {
            name = buffer.toString();
            value = "";
        }
        if (name != null && value != null) {
            value_array = paramMap.get(name);
            if (value_array == null) {
                value_array = new String[] { value };
            } else {
                value_array = addParamValue(value_array, value);
            }
            paramMap.put(name, value_array);
        }

        if (paramMap.isEmpty()) // 缺省要求=字符
            has_equals = true;
        return has_equals;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] addParamValue(T[] array, T item) {
        Class<?> clazz = array.getClass().getComponentType();
        T[] newArray = (T[]) Array.newInstance(clazz, array.length + 1);
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[array.length] = item;
        return newArray;
    }

}
