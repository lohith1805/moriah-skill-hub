import { useRef, useState, useEffect } from "react";
import { UploadCloud, File, X, Download, RefreshCw } from "lucide-react";
import clsx from "clsx";
import { FieldShell } from "./FormField";
import { useToast } from "../../context/ToastContext";

export default function FileUpload({ label, hint = "PDF, PNG or JPG — up to 10MB", error, required, accept, multiple, onChange, initialFiles = [] }) {
  const inputRef = useRef(null);
  const [files, setFiles] = useState(() => {
    if (Array.isArray(initialFiles)) return initialFiles.filter(Boolean);
    return initialFiles ? [initialFiles] : [];
  });
  const [dragOver, setDragOver] = useState(false);
  const [localError, setLocalError] = useState("");
  const [replaceIndex, setReplaceIndex] = useState(null);
  const { notify } = useToast();

  useEffect(() => {
    const nextFiles = Array.isArray(initialFiles) ? initialFiles : [initialFiles];
    const validFiles = nextFiles.filter(Boolean);
    if (validFiles.length !== files.length || validFiles.some((f, i) => f.name !== files[i]?.name)) {
      setFiles(validFiles);
    }
  }, [initialFiles]);

  const handleFiles = (fileList) => {
    const arr = Array.from(fileList);

    if (accept) {
      const allowedExtensions = accept.split(",").map(ext => ext.trim().toLowerCase());
      const invalidFiles = arr.filter(file => {
        const fileName = file.name.toLowerCase();
        return !allowedExtensions.some(ext => {
          if (ext.startsWith('.')) {
            const isMatch = fileName.endsWith(ext);
            if (isMatch) return true;
            if (ext === '.pdf' && file.type === 'application/pdf') return true;
            if (ext === '.zip' && (file.type === 'application/zip' || file.type === 'application/x-zip-compressed' || file.type === 'application/octet-stream')) return true;
            if (ext === '.png' && file.type === 'image/png') return true;
            if ((ext === '.jpg' || ext === '.jpeg') && (file.type === 'image/jpeg' || file.type === 'image/jpg')) return true;
            return false;
          }
          if (ext.endsWith('/*')) {
            const prefix = ext.replace('/*', '');
            return file.type.startsWith(prefix);
          }
          return file.type === ext;
        });
      });

      if (invalidFiles.length > 0) {
        const errorMsg = `Invalid file type. Allowed formats: ${accept}`;
        setLocalError(errorMsg);
        notify?.(errorMsg, { type: "error", title: "Upload Failed" });
        setReplaceIndex(null);
        return;
      }
    }

    setLocalError("");

    if (replaceIndex !== null) {
      const next = [...files];
      next[replaceIndex] = arr[0];
      setFiles(next);
      onChange?.(next);
      setReplaceIndex(null);
      notify?.("File replaced successfully.", { type: "success", title: "Replaced" });
    } else {
      const next = multiple ? [...files, ...arr] : arr.slice(0, 1);
      setFiles(next);
      onChange?.(next);
    }
  };

  const remove = (idx) => {
    const next = files.filter((_, i) => i !== idx);
    setFiles(next);
    onChange?.(next);
    if (replaceIndex === idx) {
      setReplaceIndex(null);
    }
  };

  const downloadFile = (f) => {
    const url = URL.createObjectURL(f);
    const a = document.createElement("a");
    a.href = url;
    a.download = f.name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const triggerReplace = (idx) => {
    setReplaceIndex(idx);
    inputRef.current?.click();
  };

  return (
    <FieldShell label={label} error={error || localError} hint={hint} required={required}>
      {files.length > 0 && !multiple ? (
        <div className="flex flex-col gap-3 p-4 rounded-xl border border-border bg-white shadow-sm">
          <div className="flex items-center gap-2 text-sm">
            <File size={16} className="text-primary-500 shrink-0" />
            <span className="flex-1 truncate font-medium text-ink-700">{files[0].name}</span>
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); downloadFile(files[0]); }}
              className="text-ink-400 hover:text-primary-600 transition-colors mr-1"
              title="Download file"
            >
              <Download size={15} />
            </button>
            <button 
              type="button" 
              onClick={(e) => { e.stopPropagation(); remove(0); }} 
              className="text-ink-400 hover:text-error-500"
              title="Remove file"
            >
              <X size={15} />
            </button>
          </div>
          <div className="flex justify-end pt-1">
            <button
              type="button"
              onClick={() => triggerReplace(0)}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary-600 hover:text-primary-700 bg-primary-50 hover:bg-primary-100/80 px-3 py-1.5 rounded-lg border border-primary-200 transition-colors cursor-pointer"
            >
              <RefreshCw size={12} />
              Replace File
            </button>
          </div>
          <input ref={inputRef} type="file" accept={accept} className="hidden" onChange={(e) => handleFiles(e.target.files)} />
        </div>
      ) : (
        <div
          onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
          onClick={() => { setReplaceIndex(null); inputRef.current?.click(); }}
          className={clsx(
            "cursor-pointer rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors",
            dragOver ? "border-primary-400 bg-primary-50" : "border-border hover:border-primary-300 hover:bg-cream-100"
          )}
        >
          <UploadCloud className="mx-auto mb-2 text-primary-500" size={26} />
          <p className="text-sm text-ink-700">
            <span className="font-medium text-primary-700">Click to upload</span> or drag and drop
          </p>
          <input ref={inputRef} type="file" accept={accept} multiple={multiple} className="hidden" onChange={(e) => handleFiles(e.target.files)} />
        </div>
      )}

      {files.length > 0 && multiple && (
        <ul className="mt-2 flex flex-col gap-1.5">
          {files.map((f, idx) => (
            <li key={idx} className="flex items-center gap-2 rounded-lg border border-border bg-white px-3 py-2 text-sm shadow-sm">
              <File size={15} className="text-primary-500 shrink-0" />
              <span className="flex-1 truncate text-ink-700">{f.name}</span>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); triggerReplace(idx); }}
                className="text-ink-400 hover:text-primary-600 transition-colors mr-1"
                title="Replace file"
              >
                <RefreshCw size={14} />
              </button>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); downloadFile(f); }}
                className="text-ink-400 hover:text-primary-600 transition-colors mr-1"
                title="Download file"
              >
                <Download size={15} />
              </button>
              <button type="button" onClick={(e) => { e.stopPropagation(); remove(idx); }} className="text-ink-400 hover:text-error-500">
                <X size={15} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </FieldShell>
  );
}
