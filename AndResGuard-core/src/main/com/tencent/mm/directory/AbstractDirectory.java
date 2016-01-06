/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main.com.tencent.mm.directory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public abstract class AbstractDirectory implements Directory {
    protected Set<String>                    mFiles;
    protected Map<String, AbstractDirectory> mDirs;

    @Override
    public Set<String> getFiles() {
        return getFiles(false);
    }

    @Override
    public Set<String> getFiles(boolean recursive) {
        if (mFiles == null) {
            loadFiles();
        }
        if (!recursive) {
            return mFiles;
        }

        Set<String> files = new LinkedHashSet<String>(mFiles);
        for (Map.Entry<String, ? extends Directory> dir : getAbstractDirs().entrySet()) {
            for (String path : dir.getValue().getFiles(true)) {
                files.add(dir.getKey() + separator + path);
            }
        }
        return files;
    }

    @Override
    public boolean containsFile(String path) {
        SubPath subpath;
        try {
            subpath = getSubPath(path);
        } catch (PathNotExist e) {
            return false;
        }

        if (subpath.dir != null) {
            return subpath.dir.containsFile(subpath.path);
        }
        return getFiles().contains(subpath.path);
    }

    @Override
    public boolean containsDir(String path) {
        SubPath subpath;
        try {
            subpath = getSubPath(path);
        } catch (PathNotExist e) {
            return false;
        }

        if (subpath.dir != null) {
            return subpath.dir.containsDir(subpath.path);
        }
        return getAbstractDirs().containsKey(subpath.path);
    }

    @Override
    public Map<String, Directory> getDirs()
        throws UnsupportedOperationException {
        return getDirs(false);
    }

    @Override
    public Map<String, Directory> getDirs(boolean recursive)
        throws UnsupportedOperationException {
        return new LinkedHashMap<String, Directory>(getAbstractDirs(recursive));
    }

    @Override
    public InputStream getFileInput(String path) throws DirectoryException {
        SubPath subpath = getSubPath(path);
        if (subpath.dir != null) {
            return subpath.dir.getFileInput(subpath.path);
        }
        if (!getFiles().contains(subpath.path)) {
            throw new PathNotExist(path);
        }
        return getFileInputLocal(subpath.path);
    }

    @Override
    public OutputStream getFileOutput(String path) throws DirectoryException {
        ParsedPath parsed = parsePath(path);
        if (parsed.dir == null) {
            getFiles().add(parsed.subpath);
            return getFileOutputLocal(parsed.subpath);
        }

        Directory dir;
        // IMPOSSIBLE_EXCEPTION
        try {
            dir = createDir(parsed.dir);
        } catch (PathAlreadyExists e) {
            dir = getAbstractDirs().get(parsed.dir);
        }
        return dir.getFileOutput(parsed.subpath);
    }

    @Override
    public Directory getDir(String path) throws PathNotExist {
        SubPath subpath = getSubPath(path);
        if (subpath.dir != null) {
            return subpath.dir.getDir(subpath.path);
        }
        if (!getAbstractDirs().containsKey(subpath.path)) {
            throw new PathNotExist(path);
        }
        return getAbstractDirs().get(subpath.path);
    }

    @Override
    public Directory createDir(String path) throws DirectoryException {
        ParsedPath parsed = parsePath(path);
        AbstractDirectory dir;
        if (parsed.dir == null) {
            if (getAbstractDirs().containsKey(parsed.subpath)) {
                throw new PathAlreadyExists(path);
            }
            dir = createDirLocal(parsed.subpath);
            getAbstractDirs().put(parsed.subpath, dir);
            return dir;
        }

        if (getAbstractDirs().containsKey(parsed.dir)) {
            dir = getAbstractDirs().get(parsed.dir);
        } else {
            dir = createDirLocal(parsed.dir);
            getAbstractDirs().put(parsed.dir, dir);
        }
        return dir.createDir(parsed.subpath);
    }

    @Override
    public boolean removeFile(String path) {
        SubPath subpath;
        try {
            subpath = getSubPath(path);
        } catch (PathNotExist e) {
            return false;
        }

        if (subpath.dir != null) {
            return subpath.dir.removeFile(subpath.path);
        }
        if (!getFiles().contains(subpath.path)) {
            return false;
        }
        removeFileLocal(subpath.path);
        getFiles().remove(subpath.path);
        return true;
    }

    protected Map<String, AbstractDirectory> getAbstractDirs() {
        return getAbstractDirs(false);
    }

    protected Map<String, AbstractDirectory> getAbstractDirs(boolean recursive) {
        if (mDirs == null) {
            loadDirs();
        }
        if (!recursive) {
            return mDirs;
        }

        Map<String, AbstractDirectory> dirs = new LinkedHashMap<String, AbstractDirectory>(mDirs);
        for (Map.Entry<String, AbstractDirectory> dir : getAbstractDirs().entrySet()) {
            for (Map.Entry<String, AbstractDirectory> subdir : dir.getValue().getAbstractDirs(
                true).entrySet()) {
                dirs.put(dir.getKey() + separator + subdir.getKey(),
                    subdir.getValue());
            }
        }
        return dirs;
    }

    private SubPath getSubPath(String path) throws PathNotExist {
        ParsedPath parsed = parsePath(path);
        if (parsed.dir == null) {
            return new SubPath(null, parsed.subpath);
        }
        if (!getAbstractDirs().containsKey(parsed.dir)) {
            throw new PathNotExist(path);
        }
        return new SubPath(getAbstractDirs().get(parsed.dir), parsed.subpath);
    }

    private ParsedPath parsePath(String path) {
        int pos = path.indexOf(separator);
        if (pos == -1) {
            return new ParsedPath(null, path);
        }
        return new ParsedPath(path.substring(0, pos), path.substring(pos + 1));
    }

    abstract protected void loadFiles();

    abstract protected void loadDirs();

    abstract protected InputStream getFileInputLocal(String name)
        throws DirectoryException;

    abstract protected OutputStream getFileOutputLocal(String name)
        throws DirectoryException;

    abstract protected AbstractDirectory createDirLocal(String name)
        throws DirectoryException;

    abstract protected void removeFileLocal(String name);


    private class ParsedPath {
        public String dir;
        public String subpath;

        public ParsedPath(String dir, String subpath) {
            this.dir = dir;
            this.subpath = subpath;
        }
    }

    private class SubPath {
        public AbstractDirectory dir;
        public String            path;

        public SubPath(AbstractDirectory dir, String path) {
            this.dir = dir;
            this.path = path;
        }
    }
}
